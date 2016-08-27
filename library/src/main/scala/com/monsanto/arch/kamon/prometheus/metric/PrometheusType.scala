package com.monsanto.arch.kamon.prometheus.metric

import com.monsanto.arch.kamon.prometheus.proto.Metrics

/** Designates which type the metric family belongs to according to the Prometheus data model.
  *
  * @param prometheusType the Prometheus type, as encoded the ProtoBuf-generated classes
  * @param text the textual representation of the type, as encoded in the Prometheus text format
  */
sealed abstract class PrometheusType(val prometheusType: Metrics.MetricType, val text: String)

object PrometheusType {
  /** Used for all counter metrics. */
  case object Counter extends PrometheusType(Metrics.MetricType.COUNTER, "counter")

  /** Used for all histogram metrics. */
  case object Histogram extends PrometheusType(Metrics.MetricType.HISTOGRAM, "histogram")
}
