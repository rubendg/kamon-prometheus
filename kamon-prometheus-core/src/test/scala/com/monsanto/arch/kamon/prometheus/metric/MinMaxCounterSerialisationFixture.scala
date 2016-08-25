package com.monsanto.arch.kamon.prometheus.metric

import java.io.ByteArrayOutputStream

import com.monsanto.arch.kamon.prometheus.converter.SnapshotConverter
import com.monsanto.arch.kamon.prometheus.proto.Metrics
import kamon.metric.SingleInstrumentEntityRecorder
import kamon.util.MilliTimestamp

/** A fixture that contains a basic min-max counter snapshot and its serialised forms.
  *
  * @author Daniel Solano Gómez
  */
trait MinMaxCounterSerialisationFixture {
  /** Name of the histogram in the basic snapshot. */
  val name = "test_min_max_counter"

  /** Timestamp for the snapshot. */
  val timestamp = MilliTimestamp.now

  /** The value of the histogram. */
  val metricValue = MetricValue.Histogram(
    Seq(MetricValue.Bucket(1, 1), MetricValue.Bucket(4, 3), MetricValue.Bucket(Double.PositiveInfinity, 3)),
    3,
    9)

  /** The labels for the metric. */
  val labels = Map(
    SnapshotConverter.KamonCategoryLabel → SingleInstrumentEntityRecorder.MinMaxCounter,
    SnapshotConverter.KamonNameLabel → name)

  /** The snapshot as a Kamon-Prometheus object. */
  val snapshot =
    Seq(MetricFamily(name, PrometheusType.Histogram, None,
      Seq(Metric(metricValue, timestamp, labels))))

  /** String representation of the labels. */
  val labelString = labels.map(lv ⇒ s"""${lv._1}="${lv._2}"""").mkString(",")

  /** The snapshot as a string. */
  val snapshotString =
    s"""# TYPE $name histogram
       |$name{$labelString,le="1.0"} 1.0 ${timestamp.millis}
       |$name{$labelString,le="4.0"} 3.0 ${timestamp.millis}
       |$name{$labelString,le="+Inf"} 3.0 ${timestamp.millis}
       |${name}_count{$labelString} 3.0 ${timestamp.millis}
       |${name}_sum{$labelString} 9.0 ${timestamp.millis}
       |""".stripMargin

  /** The snapshot in its protocol buffer serialised form. */
  val snapshotBytes = {
    val out = new ByteArrayOutputStream
    try {
      Metrics.MetricFamily.newBuilder()
        .setName(name)
        .setType(PrometheusType.Histogram.prometheusType)
        .addMetric(
          Metrics.Metric.newBuilder()
            .setTimestampMs(timestamp.millis)
            .setHistogram(
              Metrics.Histogram.newBuilder()
                .addBucket(
                  Metrics.Bucket.newBuilder()
                    .setUpperBound(1.0)
                    .setCumulativeCount(1L)
                )
                .addBucket(
                  Metrics.Bucket.newBuilder()
                    .setUpperBound(4.0)
                    .setCumulativeCount(3L)
                )
                .addBucket(
                  Metrics.Bucket.newBuilder()
                    .setUpperBound(Double.PositiveInfinity)
                    .setCumulativeCount(3L)
                )
                .setSampleCount(3L)
                .setSampleSum(9.0)
            )
            .addLabel(
              Metrics.LabelPair.newBuilder()
                .setName(SnapshotConverter.KamonCategoryLabel)
                .setValue(SingleInstrumentEntityRecorder.MinMaxCounter)
            )
            .addLabel(
              Metrics.LabelPair.newBuilder()
                .setName(SnapshotConverter.KamonNameLabel)
                .setValue(name)
            )
        )
        .build()
        .writeDelimitedTo(out)
      out.toByteArray
    } finally out.close()
  }
}
