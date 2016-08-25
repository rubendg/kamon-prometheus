package com.monsanto.arch.kamon.prometheus

import akka.actor.ActorSystem
import org.scalatest.{Matchers, WordSpec}

import scala.concurrent.Await
import scala.concurrent.duration.DurationInt

/** Tests the scenario where the Prometheus extension needs to buffer the output from Kamon.
  *
  * @author Daniel Solano Gómez
  */
class BufferedPrometheusExtensionSpec extends WordSpec with Matchers {
  "The Prometheus extension" should {
    "buffer when the refresh interval is longer than the tick interval" in {
      val system = ActorSystem()
      try {
        val extension = Prometheus(system)
        extension.isBuffered shouldBe true
        extension.listener should not be theSameInstanceAs(extension.buffer)
      } finally Await.result(system.terminate(), 3.seconds)
    }
  }
}
