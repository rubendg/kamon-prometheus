package com.monsanto.arch.kamon.prometheus

import akka.ConfigurationException
import com.typesafe.config.{Config, ConfigFactory}
import kamon.Kamon
import kamon.metric.MetricsModule
import kamon.util.ConfigTools.Syntax

import scala.collection.JavaConversions
import scala.concurrent.duration.FiniteDuration

/** A settings object used for configuring how the extension should behave.
  *
  * @param config the configuration source for the settings.  Note that in normal operation, this will come from
  *               Kamon’s actor system
  *
  * @author Daniel Solano Gómez
  */
class PrometheusSettings(config: Config) {
  config.checkValid(ConfigFactory.defaultReference(), "kamon.prometheus")
  private val prometheusConfig = config.getConfig("kamon.prometheus")

  private def metrics: MetricsModule = Kamon.metrics

  /** The extension’s endpoint will serve new results according to this value. */
  val refreshInterval: FiniteDuration = prometheusConfig.getFiniteDuration("refresh-interval")

  /** The subscriptions that determine which metrics the extension will publish to Prometheus. */
  val subscriptions: Map[String, List[String]] = {
    import JavaConversions.asScalaBuffer

    val subs: Config = prometheusConfig.getConfig("subscriptions")
    subs.firstLevelKeys.map { category ⇒
      category → subs.getStringList(category).toList
    }.toMap
  }

  /** All of the labels that will be added to all published metrics. */
  val labels: Map[String,String] = {
    val labelsConfig = prometheusConfig.getConfig("labels")
    labelsConfig.firstLevelKeys.map(name ⇒ name → labelsConfig.getString(name)).toMap
  }

  // ensure that the refresh interval is not less than the tick interval
  if (refreshInterval < metrics.settings.tickInterval) {
    val msg = s"The Prometheus refresh interval (${refreshInterval.toCoarsest}) must be equal to or greater than the Kamon tick interval (${metrics.settings.tickInterval.toCoarsest})"
    throw new ConfigurationException(msg)
  }
}
