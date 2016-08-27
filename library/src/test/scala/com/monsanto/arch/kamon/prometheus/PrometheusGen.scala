package com.monsanto.arch.kamon.prometheus

import com.monsanto.arch.kamon.prometheus.converter.SnapshotConverter
import com.monsanto.arch.kamon.prometheus.metric.{Metric, MetricFamily, MetricValue, PrometheusType}
import com.monsanto.arch.kamon.prometheus.util.strictlyIncreasing
import kamon.metric.SingleInstrumentEntityRecorder
import kamon.util.MilliTimestamp
import org.scalacheck.Gen
import org.scalacheck.Gen.Choose

import scala.collection.SortedMap

/** Various ScalaCheck generators for Kamon/Prometheus types.
  *
  * @author Daniel Solano Gómez
  */
object PrometheusGen {
  /** The default max value for a histogram. */
  val MaxInstrumentLevel = 3600000000000L

  /** Type class for the chooseExponentially generator. */
  trait ChooseExponentially[T] {
    def subtract(lhs: T, rhs: T): Double
    def add(lhs: Double, rhs: T): T
    def min(a: T, b: T): T
  }

  /** Provides default implementations of the chooseExponentially type class. */
  object ChooseExponentially {
    implicit val chooseExponentiallyLong: ChooseExponentially[Long] = new ChooseExponentially[Long] {
      override def subtract(lhs: Long, rhs: Long): Double = (lhs - rhs).toDouble
      override def min(a: Long, b: Long): Long = a.min(b)
      override def add(lhs: Double, rhs: Long): Long = lhs.toLong + rhs
    }
    implicit val chooseExponentiallyInt: ChooseExponentially[Int] = new ChooseExponentially[Int] {
      override def subtract(lhs: Int, rhs: Int): Double = (lhs - rhs).toDouble
      override def min(a: Int, b: Int): Int = a.min(b)
      override def add(lhs: Double, rhs: Int): Int = lhs.toInt + rhs
    }
    implicit val chooseExponentiallyDouble: ChooseExponentially[Double] = new ChooseExponentially[Double] {
      override def subtract(lhs: Double, rhs: Double): Double = lhs - rhs
      override def min(a: Double, b: Double): Double = a.min(b)
      override def add(lhs: Double, rhs: Double): Double = lhs + rhs
    }
  }

  /** Like Gen.choose, except that the numbers chosen should vary according to the size.  For the smallest sizes,
    * numbers will be chosen from the range (`from`, `minTo`).  As the size approaches `maxSize`, the numbers will be
    * chosen from a range of (`from`, `maxTo`).
    *
    * @param from the lower bound for all numbers
    * @param minTo the minimum upper bound
    * @param maxTo the maximum upper bound
    * @param maxSize the size at which the upper bound is reached
    * @tparam T the generate type
    * @return a `T` generator
    */
  def chooseExponentially[T: ChooseExponentially: Choose](from: T, minTo: T, maxTo: T, maxSize: Int = 100): Gen[T] = {
    val ce = implicitly[ChooseExponentially[T]]
    val factor: Double = math.log10(ce.subtract(maxTo, minTo)) / maxSize
    Gen.parameterized[T] { params ⇒
      val to: T = ce.min(ce.add(math.pow(10, factor * params.size.min(maxSize)), minTo), maxTo)
      Gen.choose[T](from, to)
    }
  }

  /** A collection of all of the valid non-control unicode characters that fit in a single Char. */
  private val UnicodeChars: Vector[Char] = {
    Char.MinValue.to(Char.MaxValue).filter(c ⇒ Character.isDefined(c) && !Character.isISOControl(c)).toVector
  }

  /** Generator for a random Unicode character. */
  val unicodeChar: Gen[Char] = {
    for (offset ← chooseExponentially(0, 100, UnicodeChars.size - 1)) yield UnicodeChars(offset)
  }

  /** Creates strings with unicode characters. */
  val unicodeString = Gen.listOf(unicodeChar).map(_.mkString)

  /** Generator for a valid Prometheus metric name. */
  val metricName = {
    for {
      first ← Gen.frequency((9, Gen.alphaChar), (1, Gen.oneOf('_', ':')))
      rest ← Gen.listOf(Gen.frequency((9, Gen.alphaNumChar), (1, Gen.oneOf('_', ':'))))
    } yield (first::rest).mkString
  }.retryUntil(!_.isEmpty)

  /** Generator for valid Prometheus label names. */
  val labelName = {
    for {
      first ← Gen.frequency(4 → Gen.alphaChar, 1 → Gen.const('_'))
      rest ← Gen.listOf(Gen.frequency(4 → Gen.alphaNumChar, 1 → Gen.const('_')))
    } yield (first :: rest).mkString
  }.retryUntil(str ⇒ !str.isEmpty && !str.startsWith("__"))

  /** Generates valid counts for counters. */
  val count = Gen.posNum[Int]

  /** Generates a set of tags that require no munging. */
  val tags: Gen[Map[String, String]] = {
    val labelEntry = for {
      key ← labelName
      value ← unicodeString
    } yield (key, value)

    Gen.mapOf(labelEntry)
  }

  /** Generates a line of help text */
  val help: Gen[String] = {
    Gen.nonEmptyListOf(
      Gen.frequency(
        20 → Gen.alphaNumChar,
        4 → Gen.oneOf(' ', '\t'),
        1 → Gen.oneOf('\\', '\n', '\"', '\'', '\r', '\u0085', '\u2028', '\u2029')))
      .map(_.mkString.trim).retryUntil(!_.isEmpty)
  }

  /** Generates a Prometheus metric value. */
  val arbitraryMetricValue: Gen[Double] = Gen.frequency(
    // the range of doubles is split into two as otherwise this causes a lot of generation failures
    5 → Gen.chooseNum(Double.MinValue, 0),
    5 → Gen.chooseNum(Double.MinPositiveValue, Double.MaxValue),
    1 → Gen.oneOf(Double.NaN, Double.PositiveInfinity, Double.NegativeInfinity)
  )

  /** Generates a timestamp. */
  val milliTimestamp: Gen[MilliTimestamp] = Gen.chooseNum(Long.MinValue, Long.MaxValue).map(new MilliTimestamp(_))

  /** Generates a new counter metric. */
  def counterMetric(name: String): Gen[Metric] = for {
    labels ← tags.map(_ ++ Map(SnapshotConverter.KamonNameLabel → name,
      SnapshotConverter.KamonCategoryLabel → SingleInstrumentEntityRecorder.Counter))
    value ← Gen.choose(0L, Long.MaxValue)
    timestamp ← milliTimestamp
  } yield Metric(MetricValue.Counter(value), timestamp, labels)

  /** Generates a new counter metric family. */
  val counter: Gen[MetricFamily] = {
    for {
      name ← metricName
      metrics ← Gen.nonEmptyListOf(counterMetric(name)).retryUntil(uniqueLabels)
    } yield MetricFamily(name, PrometheusType.Counter, None, metrics)
  }

  /** Generates a reading of an instrument. */
  val instrumentLevel = chooseExponentially(0L, 1L, MaxInstrumentLevel)

  /** Generates a list of reading from an instrument. */
  val instrumentLevels = Gen.listOf(chooseExponentially(1L, 1L, MaxInstrumentLevel))

  /** Generates a histogram metric value. */
  val histogramValue: Gen[MetricValue.Histogram] = {
    for {
      levels ← instrumentLevels.map(_.distinct.sorted).retryUntil(strictlyIncreasing(_))
      counts ← Gen.listOfN(levels.size, Gen.choose(1L, 1000000L))
    } yield {
      var totalCount = 0L
      var sum = 0.0
      val builder = Seq.newBuilder[MetricValue.Bucket]
      levels.zip(counts).foreach { b ⇒
        val (level, count) = b
        sum += count * level
        totalCount += count
        builder += MetricValue.Bucket(level, totalCount)
      }
      builder += MetricValue.Bucket(Double.PositiveInfinity, totalCount)
      MetricValue.Histogram(builder.result(), totalCount, sum)
    }
  }

  /** Generates a histogram metric. */
  def histogramMetric(name: String): Gen[Metric] = for {
    labels ← tags.retryUntil(!_.contains("le")).map(_ ++ Map(
      SnapshotConverter.KamonCategoryLabel → SingleInstrumentEntityRecorder.Histogram,
      SnapshotConverter.KamonNameLabel → name))
    value ← histogramValue
    timestamp ← milliTimestamp
  } yield Metric(value, timestamp, labels)

  /** Generates a histogram metric family. */
  val histogram: Gen[MetricFamily] = for {
    name ← metricName
    metrics ← Gen.nonEmptyListOf(histogramMetric(name)).retryUntil(uniqueLabels)
  } yield MetricFamily(name, PrometheusType.Histogram, None, metrics)

  /** Predicate for whether or not a set of min-max counter changes remains within the range. */
  def changesInRange(changes: Seq[Long]): Boolean = changes match {
    case Nil ⇒ true
    case x :: Nil ⇒ x > 1
    case x :: xs ⇒ xs.scanLeft(x)(_ + _).forall(s ⇒ (s >= 0 ) && (x < MaxInstrumentLevel))
  }

  /** Generator for a list of min-max counter changes. */
  val minMaxCounterChanges: Gen[List[Long]] = for {
    changes ← Gen.listOf(
      Gen.oneOf(
        chooseExponentially(1L, 10L, 10000L),
        chooseExponentially(1L, 10L, 10000L).map(-_)
      )
    ).retryUntil(changesInRange)
  } yield changes

  /** Generates a histogram metric value representing values for a min-max counter. */
  val minMaxCounterValue: Gen[MetricValue.Histogram] = {
    for(changes ← minMaxCounterChanges.suchThat(changesInRange)) yield {
      // convert changes to values over time
      val values = changes match {
        case Nil ⇒ Nil
        case x :: xs ⇒ xs.scanLeft(x)(_ + _)
      }
      // group those values and get the min, max, last for each group
      val groupedValues = values.grouped(5).flatMap { vals ⇒
        Seq(vals.min, vals.max, vals.last)
      }.toSeq
      // figure out how many times each value shows up
      val frequencies = groupedValues.distinct.map { v ⇒
        (v.toDouble, groupedValues.count(_ == v))
      }
      // build the histogram
      val bucketBuilder = Seq.newBuilder[MetricValue.Bucket]
      var count = 0L
      var sum = 0.0
      SortedMap(frequencies: _*).foreach { entry ⇒
        count += entry._2
        sum += entry._1 * entry._2
        bucketBuilder += MetricValue.Bucket(entry._1, count)
      }
      bucketBuilder += MetricValue.Bucket(Double.PositiveInfinity, count)
      MetricValue.Histogram(bucketBuilder.result(), count, sum)
    }
  }

  /** Generate a metric for a min-max counter.  */
  def minMaxCounterMetric(name: String): Gen[Metric] = for {
    labels ← tags.retryUntil(!_.contains("le")).map(_ ++ Map(
      SnapshotConverter.KamonCategoryLabel → SingleInstrumentEntityRecorder.MinMaxCounter,
      SnapshotConverter.KamonNameLabel → name))
    value ← minMaxCounterValue
    timestamp ← milliTimestamp
  } yield Metric(value, timestamp, labels)

  /** Generates a min-max counter metric family. */
  val minMaxCounter: Gen[MetricFamily] = for {
    name ← metricName
    metrics ← Gen.nonEmptyListOf(minMaxCounterMetric(name)).retryUntil(uniqueLabels)
  } yield MetricFamily(name, PrometheusType.Histogram, None, metrics)

  /** Predicate to ensure that all generated labels for a metric are unique. */
  private def uniqueLabels(metrics: List[Metric]): Boolean = {
    val tags = metrics.map(_.labels)
    tags.distinct == tags
  }

  /** Generates a gauge metric. */
  def gaugeMetric(name: String): Gen[Metric] = for {
    labels ← tags.retryUntil(!_.contains("le")).map(_ ++ Map(
      SnapshotConverter.KamonCategoryLabel → SingleInstrumentEntityRecorder.Gauge,
      SnapshotConverter.KamonNameLabel → name))
    value ← histogramValue
    timestamp ← milliTimestamp
  } yield Metric(value, timestamp, labels)

  /** Generates a gauge metric family. */
  val guage: Gen[MetricFamily] = for {
    name ← metricName
    metrics ← Gen.nonEmptyListOf(gaugeMetric(name)).retryUntil(uniqueLabels)
  } yield MetricFamily(name, PrometheusType.Histogram, None, metrics)

  /** Generates a new snapshot. */
  val snapshot: Gen[Seq[MetricFamily]] = Gen.listOf(Gen.oneOf(counter, histogram, minMaxCounter, guage))
}
