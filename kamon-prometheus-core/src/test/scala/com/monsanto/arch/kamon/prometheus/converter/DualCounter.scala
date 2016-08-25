package com.monsanto.arch.kamon.prometheus.converter

import kamon.metric.instrument.InstrumentFactory
import kamon.metric.{EntityRecorderFactory, GenericEntityRecorder}

/** A simple custom entity recorder that contains a pair of counters. */
class DualCounter(instrumentFactory: InstrumentFactory) extends GenericEntityRecorder(instrumentFactory) {
  val count1 = counter("count-1")
  val count2 = counter("count-2")
}

object DualCounter extends EntityRecorderFactory[DualCounter] {
  override val category: String = "dual-counter"

  override def createRecorder(instrumentFactory: InstrumentFactory): DualCounter = new DualCounter(instrumentFactory)
}
