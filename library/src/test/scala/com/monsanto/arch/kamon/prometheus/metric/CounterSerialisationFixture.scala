package com.monsanto.arch.kamon.prometheus.metric

import java.io.ByteArrayOutputStream

import com.monsanto.arch.kamon.prometheus.converter.{Mungers, SnapshotConverter}
import com.monsanto.arch.kamon.prometheus.proto.Metrics
import kamon.metric.SingleInstrumentEntityRecorder
import kamon.util.MilliTimestamp

/** A fixture that contains a basic counter snapshot and its serialised forms.
  *
  * @author Daniel Solano Gómez
  */
trait CounterSerialisationFixture {
  /** Name of the counter. */
  val name = "test_counter"

  /** Value of the counter. */
  val value = 0.0

  /** Timestamp for the basic snapshot. */
  val timestamp = MilliTimestamp.now

  /** The labels for the metric. */
  val labels = Map(
    SnapshotConverter.KamonCategoryLabel → SingleInstrumentEntityRecorder.Counter,
    SnapshotConverter.KamonNameLabel → name)

  /** String representation of the labels. */
  val labelString = labels.map(lv ⇒ s"""${lv._1}="${lv._2}"""").mkString(",")

  /** A help string. */
  val helpString = "This is a sample help string \nfor a metric."

  /** The snapshot as a kamon-prometheus object. */
  val snapshot =
    Seq(MetricFamily(name, PrometheusType.Counter, Some(helpString),
      Seq(Metric(MetricValue.Counter(value), timestamp, labels))))

  /** The snapshot in its text form. */
  val snapshotString =
    s"""# HELP $name ${helpString.replace("\n", "\\n")}
       |# TYPE $name counter
       |$name{$labelString} $value ${timestamp.millis}
       |""".stripMargin

  /** The snapshot in its protocol buffer serialised form. */
  val snapshotBytes = {
    val out = new ByteArrayOutputStream
    try {
      Metrics.MetricFamily.newBuilder()
        .setName(name)
        .setHelp(helpString)
        .setType(PrometheusType.Counter.prometheusType)
        .addMetric(
          Metrics.Metric.newBuilder()
            .setTimestampMs(timestamp.millis)
            .setCounter(
              Metrics.Counter.newBuilder().setValue(value)
            )
            .addLabel(
              Metrics.LabelPair.newBuilder()
                .setName(SnapshotConverter.KamonCategoryLabel)
                .setValue(SingleInstrumentEntityRecorder.Counter)
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
