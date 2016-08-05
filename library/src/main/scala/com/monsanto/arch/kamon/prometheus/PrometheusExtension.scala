package com.monsanto.arch.kamon.prometheus

import java.util.concurrent.atomic.AtomicReference

import akka.actor.ExtendedActorSystem
import akka.event.Logging
import com.monsanto.arch.kamon.prometheus.metric.MetricFamily
import kamon.Kamon
import kamon.metric.TickMetricSnapshotBuffer
import kamon.metric.TickMetricSnapshotBuffer.FlushBuffer
import spray.routing.Route

/** A Kamon extension that provides a Spray endpoint so that Prometheus can retrieve metrics from Kamon.
  *
  * TODO: add real documentation
  *
  * @param system the Actor system to which this class acts as an extension
  *
  * @author Daniel Solano Gómez
  */
class PrometheusExtension(system: ExtendedActorSystem) extends Kamon.Extension {
  /** Handy log reference. */
  private val log = Logging(system, classOf[PrometheusExtension])
  private val config = system.settings.config

  /** Expose the extension’s settings. */
  val settings: PrometheusSettings = new PrometheusSettings(config)

  /** Returns true if the results from the extension need to be buffered because the refresh less frequently than the
    * tick interval.
    */
  val isBuffered: Boolean = settings.refreshInterval > Kamon.metrics.settings.tickInterval

  /** Mutable cell with the latest snapshot. */
  private val snapshot = new AtomicReference[Seq[MetricFamily]]

  /** Manages the Spray endpoint. */
  private val endpoint = new PrometheusEndpoint(settings, snapshot)(system)
  /** Listens to and records metrics. */
  private[prometheus] val listener = system.actorOf(PrometheusListener.props(endpoint), "prometheus-listener")
  /** If the listener needs to listen less frequently than ticks, set up a buffer. */
  private[prometheus] val buffer = {
    if (isBuffered) {
      system.actorOf(TickMetricSnapshotBuffer.props(settings.refreshInterval, listener), "prometheus-buffer")
    } else {
      listener
    }
  }

  /** The Spray endpoint. */
  val route: Route = endpoint.route

  /** For internal use (healthchecks, other routes, etc.) */
  def requestSnapshot() = Option(snapshot.get())

  def flushBuffer() = {
    buffer ! FlushBuffer
  }

  log.info("Starting the Kamon(Prometheus) extension")
  settings.subscriptions.foreach {case (category, selections) ⇒
    selections.foreach { selection ⇒
      Kamon.metrics.subscribe(category, selection, buffer, permanently = true)
    }
  }
}
