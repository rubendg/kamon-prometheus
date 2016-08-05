package com.monsanto.arch.kamon.prometheus

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKitBase}
import com.monsanto.arch.kamon.prometheus.KamonTestKit.TestCurrentValueCollector
import com.typesafe.config.{Config, ConfigFactory}
import kamon.Kamon
import kamon.metric.SubscriptionsDispatcher.TickMetricSnapshot
import kamon.metric.instrument.{CollectionContext, Gauge, UnitOfMeasurement}
import kamon.metric._
import kamon.util.{LazyActorRef, MilliTimestamp}
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

import scala.collection.concurrent.TrieMap

/** Utilities for testing with Kamon.
  *
  * @author Daniel Solano Gómez
  */
abstract class KamonTestKit(actorSystemName: String) extends TestKitBase with KamonTestLike with WordSpecLike with Matchers with ImplicitSender with BeforeAndAfterAll {
  def config: Config = Kamon.config
  implicit lazy val system: ActorSystem = {
    Kamon.start()
    clearMetrics()
    ActorSystem(actorSystemName, config)
  }
  override protected def afterAll(): Unit = system.terminate()
}

object KamonTestKit {
  /** A test configuration that helps in a number of ways:
    *
    * 1. It sets an absurdly long tick interval, allowing the tests to control when ticks occur.
    * 2. Makes the tests less chatty log-wise
    */
  val TestConfig = ConfigFactory.parseString(
    """kamon {
      |  metric {
      |    tick-interval = 1 hour
      |    default-instrument-settings {
      |      gauge.refresh-interval = 1 hour
      |      min-max-counter.refresh-interval = 1 hour
      |    }
      |  }
      |
      |  prometheus.refresh-interval = 1 hour
      |
      |  internal-config.akka {
      |    loglevel = "OFF"
      |    stdout-loglevel = "OFF"
      |    log-dead-letters = off
      |
      |    actor.default-dispatcher {
      |      type = "akka.testkit.CallingThreadDispatcherConfigurator"
      |    }
      |  }
      |}
    """.stripMargin)

  /** Gauge current value recorder implementation that will provide all of the values in a list and then repeat the
    * last value.  The default value is zero.
    */
  class TestCurrentValueCollector(values: Seq[Long]) extends Gauge.CurrentValueCollector {
    val iterator = values.iterator
    var lastValue = 0L

    override def currentValue: Long = {
      if (iterator.hasNext) {
        lastValue = iterator.next()
      }
      lastValue
    }
  }
}

trait KamonTestLike {

  /** The start time of generated snapshots. */
  val start = MilliTimestamp.now
  /** The end time of generated snapshots. */
  val end = new MilliTimestamp(start.millis + 60000)

  /** Takes a snapshot of the given entity and returns it. */
  def snapshotOf(entities: Entity*): TickMetricSnapshot = {
    val collectionContext = CollectionContext(entities.size * 10000)
    val snapshots = entities.map {entity ⇒
      val recorder = Kamon.metrics.find(entity)
      assert(recorder.isDefined)
      val snapshot = recorder.get.collect(collectionContext)
      entity → snapshot
    }.toMap
    TickMetricSnapshot(start, end, snapshots)
  }

  /** Creates a new histogram and increments it by the given count.  Returns the entity for the counter. */
  def counter(name: String, count: Long, tags: Map[String,String] = Map.empty,
              unitOfMeasurement: UnitOfMeasurement = UnitOfMeasurement.Unknown) = {
    val c = Kamon.metrics.counter(name, tags, unitOfMeasurement)
    c.increment(count)
    Entity(name, SingleInstrumentEntityRecorder.Counter, tags)
  }

  def removeCounter(entity: Entity) = Kamon.metrics.removeCounter(entity.name, entity.tags)

  /** Creates a new histogram and records the given values.  Returns the entity for the histogram. */
  def histogram(name: String, values: Seq[Long] = Seq.empty, tags: Map[String,String] = Map.empty,
                unitOfMeasurement: UnitOfMeasurement = UnitOfMeasurement.Unknown): Entity = {
    val h = Kamon.metrics.histogram(name, tags, unitOfMeasurement)
    values.foreach(h.record)

    Entity(name, SingleInstrumentEntityRecorder.Histogram, tags)
  }

  def removeHistogram(entity: Entity) = Kamon.metrics.removeHistogram(entity.name, entity.tags)

  /** Creates a new min-max counter and applies the given increments/decrements.  After every 5 changes, the counter
    * is refreshed.  Returns the entity of the counter. */
  def minMaxCounter(name: String, changes: Seq[Long] = Seq.empty, tags: Map[String,String] = Map.empty,
                    unitOfMeasurement: UnitOfMeasurement = UnitOfMeasurement.Unknown): Entity = {
    val minMaxCounter = Kamon.metrics.minMaxCounter(name, tags, unitOfMeasurement)
    changes.grouped(5).foreach { changesChunk ⇒
      changesChunk.foreach(minMaxCounter.increment)
      minMaxCounter.refreshValues()
    }
    Entity(name, SingleInstrumentEntityRecorder.MinMaxCounter, tags)
  }

  def removeMinMaxCounter(entity: Entity) = Kamon.metrics.removeMinMaxCounter(entity.name, entity.tags)

  /** Creates a new gauge that will record the given values.  Returns the entity of the gauge. */
  def gauge(name: String, readings: Seq[Long] = Seq.empty, tags: Map[String,String] = Map.empty,
            unitOfMeasurement: UnitOfMeasurement = UnitOfMeasurement.Unknown): Entity = {
    val gauge = Kamon.metrics.gauge(name, tags, unitOfMeasurement)(new TestCurrentValueCollector(readings))
    readings.foreach(_ ⇒ gauge.refreshValue())
    Entity(name, SingleInstrumentEntityRecorder.Gauge, tags)
  }

  def removeGauge(entity: Entity) = Kamon.metrics.removeGauge(entity.name, entity.tags)

  def removeEntity(entity: Entity) = Kamon.metrics.removeEntity(entity)

  /** Forcibly flushes all subscriptions.
    *
    * Taken from https://github.com/kamon-io/Kamon/blob/57dd25e3afbfc2682b81c07161850104f32fd841/kamon-core/src/test/scala/kamon/testkit/BaseKamonSpec.scala#L54
    */
  def flushSubscriptions(): Unit = {
    val subscriptionsField = Kamon.metrics.getClass.getDeclaredField("_subscriptions")
    subscriptionsField.setAccessible(true)
    val subscriptions = subscriptionsField.get(Kamon.metrics).asInstanceOf[LazyActorRef]
    subscriptions.tell(SubscriptionsDispatcher.Tick)
  }

  def kamonSystem = {
    val systemField = Kamon.getClass.getDeclaredField("_system")
    systemField.setAccessible(true)
    systemField.get(Kamon).asInstanceOf[ActorSystem]
  }

  def kamonConfig(config: Config): Unit = {
    import scala.reflect.runtime.universe._

    val m = runtimeMirror(Kamon.getClass.getClassLoader)
    val im = m.reflect(Kamon)
    val decl = im.symbol.asType.toType.decl(TermName("config")).asTerm
    val fm = im.reflectField(decl)
    fm.set(config)

    val settings = MetricsSettings(config)

    val m1 = runtimeMirror(Kamon.getClass.getClassLoader)
    val im1 = m1.reflect(Kamon.metrics)
    val decl1 = im1.symbol.asType.toType.decl(TermName("settings")).asTerm
    val fm1 = im1.reflectField(decl1)
    fm1.set(settings)
  }

  def clearMetrics() = {
    import scala.reflect.runtime.universe._
    val m1 = runtimeMirror(Kamon.getClass.getClassLoader)
    val im1 = m1.reflect(Kamon.metrics)
    val decl1 = im1.symbol.asType.toType.decl(TermName("_trackedEntities")).asTerm
    val fm1 = im1.reflectField(decl1)
    fm1.set(TrieMap.empty[Entity, EntityRecorder])
  }
}
