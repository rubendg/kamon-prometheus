package com.monsanto.arch.kamon.prometheus

import akka.ConfigurationException
import akka.actor.ExtendedActorSystem
import akka.event.Logging
import com.monsanto.arch.kamon.prometheus.converter.SnapshotConverter
import com.monsanto.arch.kamon.prometheus.metric.MetricFamily
import kamon.Kamon
import kamon.metric.TickMetricSnapshotBuffer

/** A Kamon module that collects metrics from Kamon and stores them in a Prometheus-friendly format.
  *
  * ==Overview==
  *
  * If all you need is a Spray route that you can add to your application, you
  * do not need to use this module directly, just use the [[com.monsanto.arch.kamon.prometheus.spray.SprayEndpoint SprayEndpoint]]
  * instead.
  *
  * However, if you are interested in manually handling the metrics collected by
  * the module, read on.
  *
  * ==Getting a reference to the module==
  *
  * Unless otherwise specified, this module will be auto-started by Kamon. As
  * such, it will live under the actor system that Kamon uses.  Since Kamon no
  * longer exposes the extensions it has loaded, you will need to use either:
  *
  *  - [[Prometheus.kamonInstance]], a future from a promise that will be
  *    delivered once the extension is loaded, or
  *  - [[Prometheus.awaitKamonInstance]], a convenience function that will
  *    await on the future.
  *
  * Using either method, if the module fails to load, the future will
  * fail with the underlying cause.
  *
  * ==Interacting with the module==
  *
  * Once you have a reference to the module, you can interact with it via
  * [[ref]], an actor reference.  There are two patterns for interacting with
  * the modules:
  *
  *  1. Requesting the current snapshot, which may done without an actor
  *  1. Subscribing an actor to get updates from the module
  *
  * In either case, once you have a snapshot, you can convert it to a format
  * that Prometheus can understand by using either [[com.monsanto.arch.kamon.prometheus.metric.TextFormat.format TextFormat.format]]
  * or [[com.monsanto.arch.kamon.prometheus.metric.ProtoBufFormat.format ProtoBuf.format]].
  *
  * ===Requesting the current snapshot===
  *
  * If you just want to get the current snapshot from the module, you will
  * need to send it the [[PrometheusExtension.GetCurrentSnapshot GetCurrentSnapshot]]
  * message and it will reply with either [[PrometheusExtension.NoCurrentSnapshot NoCurrentSnapshot]]
  * or a [[PrometheusExtension.Snapshot Snapshot]] containing the current snapshot.
  *
  * {{{
  *   import com.monsanto.arch.kamon.prometheus.Prometheus
  *   import com.monsanto.arch.kamon.prometheus.PrometheusExtension._
  *   import akka.pattern.ask
  *   import scala.concurrent.duration._
  *
  *   // doing everything synchronously, which has worse performance but is
  *   // convenient
  *   val extension =  Prometheus.awaitKamonInstance()
  *   val currentState = Await.result(extension.ref.ask(GetCurrentSnapshot)(1 second), 1 second)
  *
  *   // it is also possible to do things completely asynchronously
  *   for {
  *     extension    ← Prometheus.kamonInstance
  *     currentState ← extension.ref.ask(GetCurrentSnapshot)(1 second)
  *   } {
  *     currentState match {
  *       case NoCurrentSnapshot ⇒
  *         // no state…
  *       case Snapshot(s) ⇒
  *         // do something…
  *     }
  *   }
  *
  *   // finally, you could do all of this from an actor, but that is left as
  *   // an exercise to the reader
  * }}}
  *
  * ===Subscribing to snapshot updates===
  *
  * If you are interested in getting periodic updates from the module, then
  * you will need to send it a [[PrometheusExtension.Subscribe Subscribe]]
  * message after which you will periodically receive [[PrometheusExtension.Snapshot Snapshot]]
  * messages with the latest refresh.
  *
  * {{{
  *   import akka.actor.ActorDSL._
  *   import com.monsanto.arch.kamon.prometheus.Prometheus
  *   import com.monsanto.arch.kamon.prometheus.PrometheusExtension._
  *
  *   Prometheus.kamonInstance map { ext ⇒
  *     val a = actor(new Act {
  *       become {
  *         case Snapshot(s) ⇒
  *           // do something…
  *       }
  *     })
  *     ext.ref.tell(Subscribe, a)
  *   }
  * }}}
  *
  * In order to unsubscribe from updates, just send an [[PrometheusExtension.Unsubscribe Unubscribe]]
  * message from the subscriber.  A subscriber will also be unsubscribed
  * upon its death.
  *
  * @param system the Actor system to which this class acts as an extension
  *
  * @author Daniel Solano Gómez
  */
class PrometheusExtension(system: ExtendedActorSystem) extends Kamon.Extension {
  /** Handy log reference. */
  private val log = Logging(system, classOf[PrometheusExtension])
  /** Expose the extension’s settings. */
  val settings: PrometheusSettings = new PrometheusSettings(system.settings.config)

  // ensure that the refresh interval is not less than the tick interval
  if (settings.refreshInterval < Kamon.metrics.settings.tickInterval) {
    val msg = s"The Prometheus refresh interval (${settings.refreshInterval.toCoarsest}) must be equal to or " +
        s"greater than the Kamon tick interval (${Kamon.metrics.settings.tickInterval.toCoarsest})"
    throw new ConfigurationException(msg)
  }

  /** Returns true if the results from the extension need to be buffered because the refresh less frequently than the
    * tick interval.
    */
  val isBuffered: Boolean = settings.refreshInterval > Kamon.metrics.settings.tickInterval

  /** Listens to and records metrics. */
  val ref = {
    val converter = new SnapshotConverter(settings)
    system.actorOf(PrometheusListener.props(converter), "prometheus-listener")
  }
  /** If the listener needs to listen less frequently than ticks, set up a buffer. */
  private[prometheus] val buffer = {
    if (isBuffered) {
      system.actorOf(TickMetricSnapshotBuffer.props(settings.refreshInterval, ref), "prometheus-buffer")
    } else {
      ref
    }
  }

  log.info("Starting the Kamon Prometheus module")
  settings.subscriptions.foreach {case (category, selections) ⇒
    selections.foreach { selection ⇒
      Kamon.metrics.subscribe(category, selection, buffer, permanently = true)
    }
  }
}

object PrometheusExtension {
  /** All messages that may be sent to [[com.monsanto.arch.kamon.prometheus.PrometheusExtension.ref PrometheusExtension.ref]]
    * are subtypes of this trait.
    */
  sealed trait Command

  /** The [[Command]] to request the current snapshot.  The extension will
    * reply to the sender with a [[SnapshotMessage]].
    */
  case object GetCurrentSnapshot extends Command

  /** The [[Command]] to subscribe to snapshot notifications from the extension.
    * Until an [[Unsubscribe]] is sent from the same sender (or the sender dies),
    * the extension will send [[Snapshot]] messages each time a new tick arrives.
    */
  case object Subscribe extends Command

  /** Notifies the module that the sender no longer wishes to get notifications
    * of new snapshots.
    */
  case object Unsubscribe extends Command

  /** Parent type for all messages indicating information about the current snapshot. */
  sealed trait SnapshotMessage

  /** Used as a reply to [[GetCurrentSnapshot]] indicate that there is no current snapshot. */
  case object NoCurrentSnapshot extends SnapshotMessage

  /** Contains a metrics snapshot, which may occur either as a reply to [[GetCurrentSnapshot]] or as a message
    * to a subscriber indicating there is a new snapshot.
    */
  case class Snapshot(snapshot: Seq[MetricFamily]) extends SnapshotMessage
}
