package com.monsanto.arch.kamon.prometheus.metric

import java.util.regex.Pattern

/** A group of related metrics.  See [[com.monsanto.arch.kamon.prometheus.converter.SnapshotConverter SnapshotConverter]]
  * for information about the role this class plays in mediating between Kamon’s and Prometheus’ data models.
  *
  * @param name the name of the metric, which must conform to Prometheus’ naming guidelines
  * @param prometheusType the Prometheus metric type to which this family corresponds
  * @param help optional descriptive text about the metric
  * @param metrics the individual data that make up this family
  */
case class MetricFamily(name: String, prometheusType: PrometheusType, help: Option[String], metrics: Seq[Metric]) {
  require(MetricFamily.isValidMetricFamilyName(name), "Name must be a valid Prometheus metric name.")

  /** Returns a copy of the metric family with the given help. */
  def withHelp(newHelp: String) = MetricFamily(name, prometheusType, Some(newHelp), metrics)
}

object MetricFamily {
  /** The pattern for valid Prometheus metric names. */
  val MetricNamePattern = Pattern.compile("^[a-zA-Z_:][a-zA-Z0-9_:]*$")

  /** Verifies whether a metric family name is valid. */
  def isValidMetricFamilyName(name: String): Boolean = MetricNamePattern.matcher(name).matches()
}
