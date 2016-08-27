package com.monsanto.arch.kamon.prometheus.converter

/** Allows preprocessing of metrics before they are sorted by category and converted to kamon-prometheus types.
  *
  * @author Daniel Solano Gómez
  */
trait Preprocessor {
  /** Applies the preprocessor to the snapshot to produce the new snapshot that should be used for further
    * processing.
    */
  def apply(metricSnapshot: MetricSnapshot): MetricSnapshot
}
