package com.monsanto.arch.kamon.prometheus

import akka.actor.{ActorRef, ActorSystem, PoisonPill}
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import com.monsanto.arch.kamon.prometheus.PrometheusExtension._
import com.monsanto.arch.kamon.prometheus.PrometheusListener.{GetSubscribers, Reset}
import kamon.metric.SubscriptionsDispatcher.TickMetricSnapshot
import kamon.util.MilliTimestamp
import org.scalatest.{BeforeAndAfterAll, Matchers, Outcome, WordSpecLike}

/** Tests the end-to-end functionality of the
  * [[com.monsanto.arch.kamon.prometheus.PrometheusExtension PrometheusExtension]].
  *
  * @author Daniel Solano Gómez
  */
class PrometheusExtensionSpec extends TestKit(ActorSystem("PrometheusExtensionSpec")) with ImplicitSender with WordSpecLike with Matchers with BeforeAndAfterAll {
  val extension = Prometheus.awaitKamonInstance()

  "The Prometheus extension (with tick interval = refresh interval)" should {
    "be available from Kamon" in {
      extension should not be null
    }

    "not be buffered" in {
      extension.isBuffered shouldBe false
    }

    "provide an actor for interacting with the extension" in {
      extension.ref shouldBe an[ActorRef]
    }

    "have the buffer be the same actor as the ref" in {
      extension.ref shouldBe theSameInstanceAs(extension.buffer)
    }

    "reply to a request the current snapshot" when {
      "there is none" in {
        extension.ref ! GetCurrentSnapshot
        expectMsg(NoCurrentSnapshot)
      }

      "there is a snapshot available" in {
        extension.ref !
            TickMetricSnapshot(
              new MilliTimestamp(0),
              new MilliTimestamp(1),
              Map.empty)
        extension.ref ! GetCurrentSnapshot
        expectMsg(Snapshot(Seq.empty))
      }
    }

    "subscribe actors" in {
      val subscriber = TestProbe("subscriber")
      extension.ref.tell(Subscribe, subscriber.ref)

      extension.ref ! GetSubscribers
      expectMsg(Set(subscriber.ref))
    }

    "subscribe actors only once" in {
      val subscriber = TestProbe("multiSubscriber")
      extension.ref.tell(Subscribe, subscriber.ref)
      extension.ref.tell(Subscribe, subscriber.ref)
      extension.ref.tell(Subscribe, subscriber.ref)

      extension.ref ! GetSubscribers
      expectMsg(Set(subscriber.ref))
    }

    "send snapshot notifications to subscribers" in {
      val subscribers = 0.until(10).map { i ⇒ TestProbe(s"subscriber$i")}
      subscribers.foreach(s ⇒ extension.ref.tell(Subscribe, s.ref))
      extension.ref !
          TickMetricSnapshot(
            new MilliTimestamp(0),
            new MilliTimestamp(0),
            Map.empty)
      subscribers.foreach(s ⇒ s.expectMsg(Snapshot(Seq.empty)))
    }

    "handle unsubscribe requests" in {
      val unsubscriber = TestProbe("unsubscriber")
      extension.ref.tell(Subscribe, unsubscriber.ref)

      extension.ref ! GetSubscribers
      expectMsg(Set(unsubscriber.ref))

      extension.ref.tell(Unsubscribe, unsubscriber.ref)

      extension.ref ! GetSubscribers
      expectMsg(Set.empty)
    }

    "ignore additional unsubscribe requests" in {
      val unsubscriber = TestProbe("multiUnsubscriber")
      extension.ref.tell(Subscribe, unsubscriber.ref)

      extension.ref ! GetSubscribers
      expectMsg(Set(unsubscriber.ref))

      extension.ref.tell(Unsubscribe, unsubscriber.ref)

      extension.ref ! GetSubscribers
      expectMsg(Set.empty)

      extension.ref.tell(Unsubscribe, unsubscriber.ref)

      extension.ref ! GetSubscribers
      expectMsg(Set.empty)
    }

    "handle subscriber deaths" in {
      val doomed = TestProbe("doomed")

      extension.ref.tell(Subscribe, doomed.ref)
      doomed.ref ! PoisonPill

      extension.ref ! GetSubscribers
      expectMsg(Set.empty)
    }
  }

  override protected def afterAll(): Unit = {
    super.afterAll()
    TestKit.shutdownActorSystem(system, verifySystemShutdown = true)
  }

  override protected def withFixture(test: NoArgTest): Outcome = {
    try super.withFixture(test)
    finally extension.ref ! Reset
  }
}
