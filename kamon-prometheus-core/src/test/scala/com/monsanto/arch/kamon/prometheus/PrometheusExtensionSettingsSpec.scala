package com.monsanto.arch.kamon.prometheus

import com.typesafe.config.ConfigFactory
import org.scalatest.{Matchers, WordSpec}

import scala.concurrent.duration.DurationInt

/** Tests that the Prometheus extension is correctly getting its configuration.
  *
  * @author Daniel Solano Gómez
  */
class PrometheusExtensionSettingsSpec extends WordSpec with Matchers {
  import PrometheusExtensionSettingsSpec._

  "the Prometheus settings" should {
    "load the default configuration" in {
      val settings = new PrometheusSettings(NoKamonLoggingConfig)

      settings.refreshInterval shouldBe 1.minute
      settings.subscriptions shouldBe DefaultSubscriptions
      settings.labels shouldBe Map.empty[String,String]
    }

    "respect a refresh interval setting" in {
      val config = ConfigFactory.parseString("kamon.prometheus.refresh-interval = 1000.days")
      val settings = new PrometheusSettings(config.withFallback(NoKamonLoggingConfig))
      settings.refreshInterval shouldBe 1000.days
    }

    "respect an overridden subscription setting" in {
      val config = ConfigFactory.parseString("kamon.prometheus.subscriptions.counter = [ \"foo\" ]")
      val settings = new PrometheusSettings(config.withFallback(NoKamonLoggingConfig))
      settings.subscriptions shouldBe DefaultSubscriptions.updated("counter", List("foo"))
    }

    "respect an additional subscription setting" in {
      val config = ConfigFactory.parseString("kamon.prometheus.subscriptions.foo = [ \"bar\" ]")
      val settings = new PrometheusSettings(config.withFallback(NoKamonLoggingConfig))
      settings.subscriptions shouldBe DefaultSubscriptions + ("foo" → List("bar"))
    }

    "respect configured labels" in {
      val config = ConfigFactory.parseString("kamon.prometheus.labels.foo = \"bar\"")
      val settings = new PrometheusSettings(config.withFallback(NoKamonLoggingConfig))
      settings.labels shouldBe Map("foo" → "bar")
    }
  }
}

object PrometheusExtensionSettingsSpec {
  /** Produces a configuration that disables all Akka logging from within the Kamon system. */
  val NoKamonLoggingConfig = ConfigFactory.parseString(
    """kamon.internal-config.akka {
      |  loglevel = "OFF"
      |  stdout-loglevel = "OFF"
      |}
    """.stripMargin).withFallback(ConfigFactory.defaultReference())

  /** A handy map of all the default subscriptions. */
  val DefaultSubscriptions = Map(
    "histogram" → List("**"),
    "min-max-counter" → List("**"),
    "gauge" → List("**"),
    "counter" → List("**"),
    "trace" → List("**"),
    "trace-segment" → List("**"),
    "akka-actor" → List("**"),
    "akka-dispatcher" → List("**"),
    "akka-router" → List("**"),
    "system-metric" → List("**"),
    "http-server" → List("**"),
    "spray-can-server" → List("**")
  )
}
