package com.monsanto.arch.kamon.prometheus

import akka.actor.{ExtendedActorSystem, Extension, ExtensionId, ExtensionIdProvider}
import akka.event.Logging
import kamon.Kamon

import scala.concurrent.duration.{Duration, DurationInt}
import scala.concurrent.{Await, Future, Promise}
import scala.util.control.NonFatal

/** Provides the necessary magic to register the extension with Kamon. */
object Prometheus extends ExtensionId[PrometheusExtension] with ExtensionIdProvider {
  /** Promise that will be fulfilled once Kamon starts the Prometheus extension. */
  private val kamonInstancePromise = Promise[PrometheusExtension]()

  /** Lazily initiates loading of the Kamon Prometheus module by starting
    * Kamon and returns a future that will return the instance started by
    * Kamon.
    *
    * @see [[awaitKamonInstance]]
    */
  lazy val kamonInstance: Future[PrometheusExtension] = {
    Kamon.start()
    kamonInstancePromise.future
  }

  /** Awaits and returns the value of [[kamonInstance]]
    *
    * @param timeout the amount of time to wait for Kamon to load the
    *                Prometheus module
    */
  def awaitKamonInstance(timeout: Duration = 1.second): PrometheusExtension =
    Await.result(kamonInstance, timeout)

  override def createExtension(system: ExtendedActorSystem): PrometheusExtension = {
    system.name match {
      case "kamon" ⇒
        try {
          val extension = new PrometheusExtension(system)
          kamonInstancePromise.success(extension)
          extension
        } catch {
          case NonFatal(e) ⇒
            kamonInstancePromise.failure(e)
            throw e
        }
      case other ⇒
        val log = Logging(system, this.getClass)
        log.warning("Creating a new Prometheus extension for the actor " +
          s"system $other, maybe you should just use " +
          s"Prometheus.awaitKamonInstance or Prometheus.kamonInstance?")
        new PrometheusExtension(system)
    }
  }

  override def lookup(): ExtensionId[_ <: Extension] = Prometheus
}
