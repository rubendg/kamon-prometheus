package com.monsanto.arch.kamon.prometheus.metric

import com.monsanto.arch.kamon.prometheus.PrometheusGen
import com.monsanto.arch.kamon.prometheus.metric.MetricValue.Histogram
import com.monsanto.arch.kamon.prometheus.util.strictlyIncreasing
import org.scalacheck.Gen
import org.scalactic.{Equivalence, Explicitly, TypeCheckedTripleEquals}
import org.scalatest.prop.GeneratorDrivenPropertyChecks
import org.scalatest.{Matchers, WordSpec}

/** Test suite for the various MetricValue types.
  *
  * @author Daniel Solano Gómez
  */
class MetricValueSpec extends WordSpec with Matchers with GeneratorDrivenPropertyChecks with Explicitly with TypeCheckedTripleEquals {
  "a counter" should {
    "require a non-negative count" in {
      forAll(Gen.negNum[Double] → "count" ) { (count: Double) ⇒
        an [IllegalArgumentException] shouldBe thrownBy(MetricValue.Counter(count))
      }
    }

    "accept a count of zero" in {
      val v = MetricValue.Counter(0)
      v.count shouldBe 0.0
    }

    "accept positive counts" in {
      forAll(Gen.posNum[Double] → "count" ) { (count: Double) ⇒
        val v = MetricValue.Counter(count)
        v.count shouldBe count
      }
    }
  }

  "a Histogram" when {
    import MetricValue.{Bucket => B}
    val ∞ = Double.PositiveInfinity

    "constructing" should {
      "accept valid arguments" in {
        val buckets = for {
          levels ← Gen.listOf(PrometheusGen.chooseExponentially(0.0, 10.0, Double.MaxValue))
            .retryUntil(strictlyIncreasing(_))
          counts ← Gen.listOfN(levels.size, PrometheusGen.chooseExponentially(1L, 10L, 100000L))
            .retryUntil(strictlyIncreasing(_))
        } yield  {
            levels.zip(counts).map(x ⇒ B(x._1, x._2))
        }
        forAll(buckets → "bucket values") { (buckets: Seq[B]) ⇒
          val count = buckets.lastOption.map(_.cumulativeCount).getOrElse(0L)
          val sum = buckets.foldLeft(0.0 → 0L) { (sumAndLastCount, b) ⇒
            val (sum, lastCount) = sumAndLastCount
            val newSum = sum + (b.upperBound * (b.cumulativeCount - lastCount))
            (newSum, b.cumulativeCount)
          }._1
          val bucketsWithInfinity = buckets :+ B(∞, count)
          val metric = MetricValue.Histogram(bucketsWithInfinity, count, sum)

          metric.buckets shouldBe bucketsWithInfinity
          metric.sampleCount shouldBe count
          metric.sampleSum shouldBe sum
        }
      }

      "reject buckets that are not strictly increasing by level" in {
        the [IllegalArgumentException] thrownBy {
          val monotonic = Seq(B(0, 2), B(1, 3), B(1, 4), B(∞, 4))
          MetricValue.Histogram(monotonic, 4, 7)
        } should have message "requirement failed: Buckets must be in strictly increasing order by level"

        the [IllegalArgumentException] thrownBy {
          val decreasing = Seq(B(0, 2), B(4, 3), B(3, 4), B(∞, 4))
          MetricValue.Histogram(decreasing, 4, 7)
        } should have message "requirement failed: Buckets must be in strictly increasing order by level"
      }

      "reject buckets that do not add data" in {
        the [IllegalArgumentException] thrownBy {
          val hasEmpty = Seq(B(0, 2), B(1, 3), B(3, 3), B(∞, 3))
          MetricValue.Histogram(hasEmpty, 3, 3)
        } should have message "requirement failed: Every finite bucket must add to the count"

        the [IllegalArgumentException] thrownBy {
          val hasNegative = Seq(B(0, 2), B(1, 3), B(3, 2), B(∞, 2))
          MetricValue.Histogram(hasNegative, 3, 3)
        } should have message "requirement failed: Every finite bucket must add to the count"
      }

      "reject an empty buckets with a helpful error" in {
        the [IllegalArgumentException] thrownBy {
          MetricValue.Histogram(Seq.empty[B], 0, 0)
        } should have message "requirement failed: Buckets must not be empty, a (∞, 0) bucket indicates no values"
      }

      "reject buckets without an ∞ element" in {
        the [IllegalArgumentException] thrownBy {
          val missingInfinityBucket = Seq(B(2, 2))
          MetricValue.Histogram(missingInfinityBucket, 2, 4)
        } should have message "requirement failed: The last bucket must have the value +∞"
      }

      "the ∞ bucket does not change the cumulative count" in {
        the [IllegalArgumentException] thrownBy {
          val hasInfiniteValue = Seq(B(0, 2), B(1, 3), B(3, 4), B(∞, 5))
          MetricValue.Histogram(hasInfiniteValue, 5, ∞)
        } should have message "requirement failed: ∞ bucket must not modify cumulative count"

        the [IllegalArgumentException] thrownBy {
          val subtractsCount = Seq(B(0, 2), B(1, 3), B(3, 4), B(∞, 3))
          MetricValue.Histogram(subtractsCount, 3, 6 - ∞)
        } should have message "requirement failed: ∞ bucket must not modify cumulative count"

        the [IllegalArgumentException] thrownBy {
          val nonZeroInfinity = Seq(B(∞, 3))
          MetricValue.Histogram(nonZeroInfinity, 3, ∞)
        } should have message "requirement failed: ∞ bucket must not modify cumulative count"
      }

      "the sample count does not match the buckets" in {
        the [IllegalArgumentException] thrownBy {
          val nonZeroInfinity = Seq(B(1, 1), B(2, 2), B(3, 3), B(∞, 3))
          MetricValue.Histogram(nonZeroInfinity, 2, 6)
        } should have message "requirement failed: The sampleCount (2) must match the count from the buckets (3)"
      }

      "the sample sum does not match the buckets" in {
        the [IllegalArgumentException] thrownBy {
          val nonZeroInfinity = Seq(B(1, 1), B(2, 2), B(3, 3), B(∞, 3))
          MetricValue.Histogram(nonZeroInfinity, 3, 7)
        } should have message "requirement failed: The sampleSum (7.0) must match the sum derived from the buckets (6.0)"
      }
    }

    "building from a Histogram.Snapshot" should {
      val closeEnoughSums = new Equivalence[MetricValue.Histogram]  {
        override def areEquivalent(a: Histogram, b: Histogram): Boolean = {
          a.buckets == b.buckets && a.sampleCount == b.sampleCount &&
            (a.sampleSum == b.sampleSum || math.abs(a.sampleSum - b.sampleSum) < 10 * math.ulp(a.sampleSum))
        }
      }

      "empty snapshots" in new HistogramExtractorFixture {
        val snapshot = mockSnapshot()
        val result = MetricValue.Histogram(snapshot)
        result shouldBe MetricValue.Histogram(Seq(B(∞, 0)), 0 ,0)
      }

      "single-bucket snapshots" in new HistogramExtractorFixture {
        forAll(recordsN(1) → "record") { (record: Records) ⇒
          val snapshot = mockSnapshot(record)
          val result = MetricValue.Histogram(snapshot)
          val r = record.head
          result shouldBe MetricValue.Histogram(Seq(B(r.level, r.count), B(∞, r.count)),
            snapshot.numberOfMeasurements, snapshot.sum)
        }
      }

      "two-bucket snapshots" in new HistogramExtractorFixture {
        forAll(recordsN(2) → "records") { (records: Records) ⇒
          whenever(records.size == 2) {
            val snapshot = mockSnapshot(records)
            val result = MetricValue.Histogram(snapshot)
            val first :: second :: Nil = records
            val totalCount = first.count + second.count
            val expected =
              MetricValue.Histogram(
                Seq(
                  B(first.level.toDouble, first.count),
                  B(second.level.toDouble, totalCount),
                  B(∞, totalCount)),
                snapshot.numberOfMeasurements,
                snapshot.sum)
            (result should === (expected)) (determined by closeEnoughSums)
          }
        }
      }

      "arbitrary snapshots" in new HistogramExtractorFixture {
        forAll(records → "records") { (records: Records) ⇒
          val snapshot = mockSnapshot(records)
          val result = MetricValue.Histogram(snapshot)

          var cumulativeCount = 0L
          var sum = 0.0
          val expectedBuilder = Seq.newBuilder[MetricValue.Bucket]
          expectedBuilder.sizeHint(records.size)
          records.foreach { record ⇒
            cumulativeCount += record.count
            sum += record.count * record.level
            expectedBuilder += MetricValue.Bucket(record.level, cumulativeCount)
          }
          val expected = MetricValue.Histogram(expectedBuilder.result() :+ B(∞, cumulativeCount), cumulativeCount, sum)
          result shouldBe expected
        }
      }
    }
  }
}
