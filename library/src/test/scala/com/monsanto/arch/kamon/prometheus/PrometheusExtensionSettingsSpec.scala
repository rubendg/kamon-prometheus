package com.monsanto.arch.kamon.prometheus

import akka.ConfigurationException
import akka.actor.ActorSystem
import akka.testkit.TestKit
import com.typesafe.config.ConfigFactory
import kamon.Kamon
import org.scalatest.{Matchers, WordSpecLike}

import scala.concurrent.duration.DurationInt

/** Tests that the Prometheus extension is correctly getting its configuration.
  *
  * @author Daniel Solano Gómez
  */
class PrometheusExtensionSettingsSpec extends KamonTestKit("PrometheusExtensionSettingsSpec") {
  import PrometheusExtensionSettingsSpec._

  "the Prometheus extension" when {
    "loading settings" should {
      "load the default configuration" in {
        val _system = ActorSystem("ext_test_0", NoKamonLoggingConfig)
        val settings = Prometheus(_system).settings

        settings.refreshInterval shouldBe 1.minute
        settings.subscriptions shouldBe DefaultSubscriptions
        settings.labels shouldBe Map.empty[String,String]
        _system.terminate()
      }

      "reject configurations where the refresh interval is too short" in {
        val config = ConfigFactory.parseString("kamon.prometheus.refresh-interval = 30 milliseconds")
        val _system = ActorSystem("ext_test_1", config.withFallback(NoKamonLoggingConfig))
        the [ConfigurationException] thrownBy {
          Prometheus(_system)
        } should have message "The Prometheus refresh interval (30 milliseconds) must be equal to or greater than the Kamon tick interval (10 seconds)"
        _system.terminate()
      }

      "respect a refresh interval setting" in {
        val config = ConfigFactory.parseString("kamon.prometheus.refresh-interval = 1000.days")
        val _system = ActorSystem("ext_test_2", config.withFallback(NoKamonLoggingConfig))
        Prometheus(_system).settings.refreshInterval shouldBe 1000.days
        _system.terminate()
      }

      "respect an overridden subscription setting" in {
        val config = ConfigFactory.parseString("kamon.prometheus.subscriptions.counter = [ \"foo\" ]")
        val _system = ActorSystem("ext_test_3", config.withFallback(NoKamonLoggingConfig))
        Prometheus(_system).settings.subscriptions shouldBe DefaultSubscriptions.updated("counter", List("foo"))
        _system.terminate()
      }

      "respect an additional subscription setting" in {
        val config = ConfigFactory.parseString("kamon.prometheus.subscriptions.foo = [ \"bar\" ]")
        val _system = ActorSystem("ext_test_4", config.withFallback(NoKamonLoggingConfig))
        Prometheus(_system).settings.subscriptions shouldBe DefaultSubscriptions + ("foo" → List("bar"))
        _system.terminate()
      }

      "respect configured labels" in {
        val config = ConfigFactory.parseString("kamon.prometheus.labels.foo = \"bar\"")
        val _system = ActorSystem("ext_test_5", config.withFallback(NoKamonLoggingConfig))
        Prometheus(_system).settings.labels shouldBe Map("foo" → "bar")
        _system.terminate()
      }
    }

    "applying the refresh interval setting" should {
      "enable buffering when the refresh interval is longer than the tick interval" in {
        val config = ConfigFactory.parseString(
          """kamon.prometheus.refresh-interval = 5 minute
            |kamon.metric.tick-interval = 2 minutes
          """.stripMargin).withFallback(NoKamonLoggingConfig)

        val _system = ActorSystem("ext_test_6", config)

        val extension = Prometheus(_system)
        extension.isBuffered shouldBe true
        extension.listener should not be theSameInstanceAs(extension.buffer)
        _system.terminate()
      }

      "not buffer when the refresh interval is the same as the tick interval" in {
        val config = ConfigFactory.parseString(
          """kamon.prometheus.refresh-interval = 42 days
            |kamon.metric.tick-interval = 42 days
          """.stripMargin).withFallback(NoKamonLoggingConfig)

        val origConfig = Kamon.config
        kamonConfig(config)

        val _system = ActorSystem("ext_test_7", config)

        val extension = Prometheus(_system)
        extension.isBuffered shouldBe false
        extension.listener shouldBe theSameInstanceAs(extension.buffer)
        _system.terminate()
        kamonConfig(origConfig)
      }
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
    """.stripMargin).withFallback(ConfigFactory.load())

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
