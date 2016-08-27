package com.monsanto.arch.kamon.prometheus

import akka.actor.{Actor, ActorRef, Props, Terminated}
import akka.event.Logging
import com.monsanto.arch.kamon.prometheus.PrometheusExtension._
import com.monsanto.arch.kamon.prometheus.PrometheusListener.{GetSubscribers, Reset}
import com.monsanto.arch.kamon.prometheus.converter.SnapshotConverter
import com.monsanto.arch.kamon.prometheus.metric.MetricFamily
import kamon.metric.SubscriptionsDispatcher.TickMetricSnapshot

/** This actor performs the core work of the Kamon Prometheus extension.
  *
  *  - It receives ticks from Kamon and converts them to a
  *    Promtheus-friendly representation
  *  - It manages a set of subscribers
  *  - It updates its subscribers with snapshots as they occur
  *
  * @param convert a function that will take a Kamon [[TickMetricSnapshot]] and
  *                convert it to a Prometheus snapshot
  *
  * @author Daniel Solano Gómez
  */
private[prometheus] class PrometheusListener(convert: SnapshotConverter) extends Actor {
  private val log = Logging(context.system, this)

  override def receive = receiver(None, Set.empty)

  private def receiver(currentSnapshot: Option[Seq[MetricFamily]],
                       subscribers: Set[ActorRef]): Receive = {
    case tick: TickMetricSnapshot ⇒
      log.debug(s"Got a tick: $tick")
      val newSnapshot = convert(tick)
      val newSnapshotMessage = Snapshot(newSnapshot)
      subscribers.foreach { s ⇒
        log.debug(s"Sending snapshot to $s")
        s ! newSnapshotMessage
      }
      context.become(receiver(Some(newSnapshot), subscribers))

    case GetCurrentSnapshot ⇒
      currentSnapshot match {
        case None ⇒
          log.debug(s"Got a request for a current snapshot from ${sender()}, " +
              "replying NoCurrentSnapshot")
          sender() ! NoCurrentSnapshot
        case Some(s) ⇒
          log.debug(s"Got a request for a current snapshot from ${sender()}, " +
              "replying with current snapshot")
          sender() ! Snapshot(s)
      }

    case Subscribe ⇒
      log.debug(s"Subscribing ${sender()}")
      context.watch(sender())
      context.become(receiver(currentSnapshot, subscribers + sender()))

    case Unsubscribe ⇒
      log.debug(s"Unsubscribing ${sender()}")
      context.unwatch(sender())
      context.become(receiver(currentSnapshot, subscribers - sender()))

    case Terminated(subscriber) ⇒
      log.debug(s"$subscriber terminated, removing from subscriber list")
      context.become(receiver(currentSnapshot, subscribers - subscriber))

    case GetSubscribers ⇒
      sender() ! subscribers

    case Reset ⇒
      subscribers.foreach(context.unwatch)
      context.become(receiver(None, Set.empty))

    case x ⇒
      log.warning(s"Got an unexpected $x")
  }
}

private[prometheus] object PrometheusListener {
  /** Provides the props to create a new PrometheusListener. */
  def props(converter: SnapshotConverter): Props = Props(new PrometheusListener(converter))

  /** Internal command for getting the set of subscribers, for testing only. */
  case object GetSubscribers
  /** Internal command for resetting the state of the listner, for testing only. */
  case object Reset
}
