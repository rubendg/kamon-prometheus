package com.monsanto.arch.kamon.prometheus.metric

/** Generic interface for reading and writing snapshots to some sort of custom serialisation format.
  *
  * @tparam T the type of the serialised format
  *
  * @author Daniel Solano Gómez
  */
trait SerialisationFormat[T] {
  /** Serialises the snapshot. */
  def format(snapshot: Seq[MetricFamily]): T

  /** Constructs a snapshot from its serialised format. */
  def parse(source: T): Seq[MetricFamily]
}
