package com.monsanto.arch.kamon.prometheus.metric

import com.monsanto.arch.kamon.prometheus.PrometheusGen
import org.scalatest.prop.GeneratorDrivenPropertyChecks
import org.scalatest.{Matchers, WordSpec}

/** Tests for serialising to/from the protocol buffer wire format.
  *
  * @author Daniel Solano Gómez
  */
class ProtoBufFormatSpec extends WordSpec with Matchers with GeneratorDrivenPropertyChecks {
  "the protocol buffer support" should {
    "write a basic counter snapshot" in new CounterSerialisationFixture {
      ProtoBufFormat.format(snapshot) shouldBe snapshotBytes
    }

    "read a simple counter snapshot" in new CounterSerialisationFixture {
      ProtoBufFormat.parse(snapshotBytes) shouldBe snapshot
    }

    "write a basic histogram snapshot" in new HistogramSerialisationFixture {
      ProtoBufFormat.format(snapshot) shouldBe snapshotBytes
    }

    "read a basic histogram snapshot" in new HistogramSerialisationFixture {
      ProtoBufFormat.parse(snapshotBytes) shouldBe snapshot
    }

    "write a basic min-max-counter snapshot" in new MinMaxCounterSerialisationFixture {
      ProtoBufFormat.format(snapshot) shouldBe snapshotBytes
    }

    "read a basic min-max-counter snapshot" in new MinMaxCounterSerialisationFixture {
      ProtoBufFormat.parse(snapshotBytes) shouldBe snapshot
    }

    "write a basic gauge snapshot" in new GaugeSerialisationFixture {
      ProtoBufFormat.format(snapshot) shouldBe snapshotBytes
    }

    "read a basic gauge snapshot" in new GaugeSerialisationFixture {
      ProtoBufFormat.parse(snapshotBytes) shouldBe snapshot
    }

    "round-trip arbitrary snapshots" in {
      forAll(PrometheusGen.snapshot → "snapshot", sizeRange(30)) { (snapshot: Seq[MetricFamily]) ⇒
        ProtoBufFormat.parse(ProtoBufFormat.format(snapshot)) shouldBe snapshot
      }
    }
  }
}
