package com.monsanto.arch.kamon.prometheus

import org.scalatest.{Matchers, WordSpec}

/** Tests the scenario where the Prometheus extension needs to buffer the output from Kamon.
  *
  * @author Daniel Solano Gómez
  */
class BufferedPrometheusExtensionSpec extends WordSpec with Matchers {
  "The Prometheus extension" should {
    "buffer when the refresh interval is longer than the tick interval" in {
      val extension = Prometheus.awaitKamonInstance()
      extension.isBuffered shouldBe true
      extension.listener should not be theSameInstanceAs(extension.buffer)
    }
  }
}
