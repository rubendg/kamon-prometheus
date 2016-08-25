package com.monsanto.arch.kamon.prometheus.metric

import com.monsanto.arch.kamon.prometheus.PrometheusGen
import com.monsanto.arch.kamon.prometheus.util.strictlyIncreasing
import kamon.metric.instrument.{CollectionContext, Histogram, InstrumentSnapshot}
import org.scalacheck.Gen

/** Utility for testing the histogram snapshot conversion function.
  *
  * @author Daniel Solano Gómez
  */
trait HistogramExtractorFixture {
  type Records = Seq[Histogram.Record]

  def mockSnapshot(records: Seq[Histogram.Record]): Histogram.Snapshot = {
    new HistogramExtractorFixture.MockSnapshot(records)
  }

  def mockSnapshot(): Histogram.Snapshot = mockSnapshot(Seq.empty[Histogram.Record])

  def recordsN(n: Int) = HistogramExtractorFixture.recordsN(n)

  val records = HistogramExtractorFixture.records
}

object HistogramExtractorFixture {
  /** Generator for bucket levels. */
  val level = PrometheusGen.chooseExponentially(1L, 1L, 3600000000000L)

  /** Generator for bucket counts. */
  val count = PrometheusGen.chooseExponentially(1L, 100L, 1000000L)

  /** Generator of n records. */
  def recordsN(n: Int): Gen[Seq[Histogram.Record]] = for {
    levels ← Gen.listOfN(n, level).retryUntil(strictlyIncreasing(_)).suchThat(_.size == n)
    counts ← Gen.listOfN(n, count).suchThat(_.size == n)
  } yield levels.zip(counts).map(p ⇒ MockRecord(p._1, p._2))

  /** Generator for arbitrary sequences of records. */
  val records: Gen[Seq[Histogram.Record]] = for {
    levels ← Gen.listOf(level).retryUntil(strictlyIncreasing(_))
    counts ← Gen.listOfN(levels.size, count)
  } yield levels.zip(counts).map(p ⇒ MockRecord(p._1, p._2))

  /** A mock implementation of a histogram snapshot record. */
  case class MockRecord(level: Long, count: Long) extends Histogram.Record {
    override def rawCompactRecord = ???
  }

  /** A mock implementation of a histogram snapshot. */
  class MockSnapshot(records: Seq[Histogram.Record]) extends Histogram.Snapshot {
    override def numberOfMeasurements: Long = records.foldLeft(0L) { (count, record) ⇒
      count + record.count
    }

    override def max: Long = ???

    override def merge(that: InstrumentSnapshot, context: CollectionContext): InstrumentSnapshot = ???

    override def merge(that: Histogram.Snapshot, context: CollectionContext): Histogram.Snapshot = ???

    override def percentile(percentile: Double): Long = ???

    override def min: Long = ???

    override def sum: Long = records.foldLeft(0L) { (sum, record) ⇒ sum + record.level * record.count }

    override def recordsIterator: Iterator[Histogram.Record] = records.iterator
  }
}
