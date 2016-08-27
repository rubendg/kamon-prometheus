package com.monsanto.arch.kamon.prometheus.metric

import java.io.ByteArrayOutputStream

import com.monsanto.arch.kamon.prometheus.converter.SnapshotConverter
import com.monsanto.arch.kamon.prometheus.proto.Metrics
import kamon.metric.SingleInstrumentEntityRecorder
import kamon.util.MilliTimestamp

/** A fixture that contains a basic histogram snapshot and its serialised forms.
  *
  * @author Daniel Solano Gómez
  */
trait HistogramSerialisationFixture {
  /** Name of the histogram in the basic snapshot. */
  val name = "test_histogram"

  /** Timestamp for the snapshot. */
  val timestamp = MilliTimestamp.now

  /** The value of the histogram. */
  val metricValue = MetricValue.Histogram(
    Seq(MetricValue.Bucket(1, 5), MetricValue.Bucket(4, 7), MetricValue.Bucket(Double.PositiveInfinity, 7)),
    7,
    13)

  /** The labels for the metric. */
  val labels = Map(
    SnapshotConverter.KamonCategoryLabel → SingleInstrumentEntityRecorder.Histogram,
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
       |$name{$labelString,le="1.0"} 5.0 ${timestamp.millis}
       |$name{$labelString,le="4.0"} 7.0 ${timestamp.millis}
       |$name{$labelString,le="+Inf"} 7.0 ${timestamp.millis}
       |${name}_count{$labelString} 7.0 ${timestamp.millis}
       |${name}_sum{$labelString} 13.0 ${timestamp.millis}
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
                    .setCumulativeCount(5L)
                )
                .addBucket(
                  Metrics.Bucket.newBuilder()
                    .setUpperBound(4.0)
                    .setCumulativeCount(7L)
                )
                .addBucket(
                  Metrics.Bucket.newBuilder()
                    .setUpperBound(Double.PositiveInfinity)
                    .setCumulativeCount(7L)
                )
                .setSampleCount(7L)
                .setSampleSum(13.0)
            )
            .addLabel(
              Metrics.LabelPair.newBuilder()
                .setName(SnapshotConverter.KamonCategoryLabel)
                .setValue(SingleInstrumentEntityRecorder.Histogram)
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
