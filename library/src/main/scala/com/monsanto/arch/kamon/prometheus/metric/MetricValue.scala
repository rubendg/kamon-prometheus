package com.monsanto.arch.kamon.prometheus.metric

import com.monsanto.arch.kamon.prometheus.util.strictlyIncreasing

/** Generic trait for all possible metric values. */
sealed trait MetricValue

object MetricValue {
  /** Counters are represented as doubles, despite being integral.  They must be non-negative. */
  case class Counter(count: Double) extends MetricValue {
    require(count >= 0)
  }

  /** A histogram consists of series of buckets, a sample count, and a sample sum.  The buckets must be in strictly
    * increasing order by level.  Also, the counts in each bucket is cumulative and should therefore also be strictly
    * increasing (i.e. the bucket is not empty), with the possible exception of the last bucket, which is special.
    * The last bucket must have the level +∞ and should have a cumulative count that is the same as the total count
    * for the whole histogram.  The sample count and sum should match the values from the histogram.
    */
  case class Histogram(buckets: Seq[Bucket], sampleCount: Long, sampleSum: Double) extends MetricValue {
    require(strictlyIncreasing(buckets.map(_.upperBound)), "Buckets must be in strictly increasing order by level")
    require(strictlyIncreasing(buckets.dropRight(1).map(_.cumulativeCount)), "Every finite bucket must add to the count")
    require(buckets.nonEmpty, "Buckets must not be empty, a (∞, 0) bucket indicates no values")
    require(buckets.last.upperBound == Double.PositiveInfinity, "The last bucket must have the value +∞")
    require(buckets.takeRight(2) match {
      case Bucket(Double.PositiveInfinity, x) :: Nil ⇒ x == 0
      case Bucket(_, x) :: Bucket(Double.PositiveInfinity, y) :: Nil ⇒ x == y
    }, "∞ bucket must not modify cumulative count")
    require(sampleCount == buckets.last.cumulativeCount,
      s"The sampleCount ($sampleCount) must match the count from the buckets (${buckets.last.cumulativeCount})")
    val bucketSum = {
      buckets.dropRight(1).foldLeft(0.0 → 0L) { (sumAndLastCount, bucket) ⇒
        val (lastSum, lastCount) = sumAndLastCount
        val thisCount = bucket.cumulativeCount - lastCount
        val sum = lastSum + bucket.upperBound * thisCount
        (sum, bucket.cumulativeCount)
      }._1
    }
    require(sampleSum == bucketSum || (math.abs(sampleSum - bucketSum) < 5 * math.ulp(bucketSum)) ,
      s"The sampleSum ($sampleSum) must match the sum derived from the buckets ($bucketSum)")
  }

  /** Provides a factory function for creating new instances. */
  object Histogram {
    /** Creates a new value directly from a Kamon snapshot. */
    def apply(snapshot: kamon.metric.instrument.Histogram.Snapshot): Histogram = {
      val rawValues = {
        val builder = Seq.newBuilder[(Double, Long)]
        snapshot.recordsIterator.foreach(r ⇒ builder += r.level.toDouble → r.count)
        builder.result()
      }
      val sumValues = rawValues match {
        case Nil ⇒ Nil
        case h :: t ⇒ t.scanLeft(h)((lhs, rhs) ⇒ (rhs._1, lhs._2 + rhs._2))
      }
      val finiteBuckets = sumValues.map(b ⇒ MetricValue.Bucket(b._1.toDouble, b._2))
      val sum = rawValues.foldLeft(0.0) { (sum, pair) ⇒ sum + pair._1 * pair._2 }
      val count = finiteBuckets.lastOption.map(_.cumulativeCount).getOrElse(0L)
      val allBuckets = finiteBuckets :+ MetricValue.Bucket(Double.PositiveInfinity, count)
      Histogram(allBuckets, count, sum)
    }
  }

  /** A bucket in a histogram. */
  case class Bucket(upperBound: Double, cumulativeCount: Long)
}
