package com.monsanto.arch.kamon.prometheus

import akka.ConfigurationException
import akka.actor.ExtendedActorSystem
import akka.event.Logging
import kamon.Kamon
import kamon.metric.TickMetricSnapshotBuffer
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

  /** Manages the Spray endpoint. */
  private[prometheus] val endpoint = new PrometheusEndpoint(settings)(system)
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

  log.info("Starting the Kamon(Prometheus) extension")
  settings.subscriptions.foreach {case (category, selections) ⇒
    selections.foreach { selection ⇒
      Kamon.metrics.subscribe(category, selection, buffer, permanently = true)
    }
  }
}
