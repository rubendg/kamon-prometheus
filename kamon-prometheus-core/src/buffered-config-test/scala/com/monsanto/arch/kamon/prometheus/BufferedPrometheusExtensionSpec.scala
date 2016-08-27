package com.monsanto.arch.kamon.prometheus

import org.scalatest.{Matchers, WordSpec}

/** Tests the scenario where the Prometheus extension needs to buffer the output from Kamon.
  *
  * @author Daniel Solano Gómez
  */
class BufferedPrometheusExtensionSpec extends WordSpec with Matchers {
  val extension = Prometheus.awaitKamonInstance()

  "The Prometheus extension" when {
    "the refresh interval is longer than the tick interval" should {
      "report that it is buffered" in {
        extension.isBuffered shouldBe true
      }

      "have a distinct buffer actor from the main ref" in {
        extension.ref should not be theSameInstanceAs(extension.buffer)
      }
    }
  }
}
