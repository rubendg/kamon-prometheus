package com.monsanto.arch.kamon.prometheus

import akka.ConfigurationException
import org.scalatest.{Matchers, WordSpec}

/** Tests the scenario where the Prometheus extension needs to buffer the output from Kamon.
  *
  * @author Daniel Solano Gómez
  */
class InvalidPrometheusExtensionSpec extends WordSpec with Matchers {
  "The Prometheus extension" should {
    "reject configurations where the refresh interval is too short" in {
      the [ConfigurationException] thrownBy {
        Prometheus.awaitKamonInstance()
      } should have message "The Prometheus refresh interval (30 milliseconds) must be equal to or greater than the Kamon tick interval (10 seconds)"
    }
  }
}
