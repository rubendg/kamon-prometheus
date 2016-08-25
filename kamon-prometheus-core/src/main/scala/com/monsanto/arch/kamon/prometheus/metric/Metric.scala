package com.monsanto.arch.kamon.prometheus.metric

import java.util.regex.Pattern

import kamon.metric.SingleInstrumentEntityRecorder
import kamon.util.MilliTimestamp

/** A metric represents a single item in Prometheus’ data model.
  * 
  * @param value the metric value, note that all values are coerced to doubles
  * @param timestamp the time of this snapshot
  * @param labels the labels for the metric, which should conform to Prometheus’ guidelines
  *
  * @see [[com.monsanto.arch.kamon.prometheus.converter.SnapshotConverter SnapshotConverter]]
  *
  * @author Daniel Solano Gómez
  */
case class Metric(value: MetricValue, timestamp: MilliTimestamp, labels: Map[String,String] = Map.empty) {
  // require that everything has values that Prometheus will accept
  labels.foreach {case (key, _) ⇒
    require(Metric.isValidLabelName(key), s"Invalid label name: ‘$key’")
  }
}

object Metric {
  /** Verifies that a label name is generally valid. */
  private val LabelNamePattern = Pattern.compile("^[a-zA-Z_][a-zA-Z0-9_]*$")

  /** Returns true if the given name is a valid, non-reserved Prometheus label name. */
  def isValidLabelName(name: String): Boolean = LabelNamePattern.matcher(name).matches() && !name.startsWith("__")
}
