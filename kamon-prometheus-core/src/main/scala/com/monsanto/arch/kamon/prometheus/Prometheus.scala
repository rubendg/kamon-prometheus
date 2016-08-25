package com.monsanto.arch.kamon.prometheus

import akka.actor.{ExtendedActorSystem, Extension, ExtensionIdProvider, ExtensionId}

/** Provides the necessary magic to register the extension with Kamon. */
object Prometheus extends ExtensionId[PrometheusExtension] with ExtensionIdProvider {
  override def createExtension(system: ExtendedActorSystem): PrometheusExtension = new PrometheusExtension(system)

  override def lookup(): ExtensionId[_ <: Extension] = Prometheus
}
