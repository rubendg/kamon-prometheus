package com.monsanto.arch.kamon.prometheus.converter

import kamon.metric.instrument.{Memory, Time, Gauge, InstrumentFactory}
import kamon.metric.{EntityRecorderFactory, GenericEntityRecorder}

/** A custom metric type that has one of each instrument. */
class Smorgasbord(instrumentFactory: InstrumentFactory) extends GenericEntityRecorder(instrumentFactory) with Gauge.CurrentValueCollector {
  var iterator = Seq.empty[Long].iterator
  var lastValue = 0L

  val aCounter = counter("a-counter")
  val aHistogram = histogram("a-histogram", Time.Milliseconds)
  val aMinMaxCounter = minMaxCounter("a-min-max-counter", Memory.KiloBytes)
  val aGage = gauge("a-gauge", this)

  def setGaugeReadings(readings: Seq[Long]): Unit = {
    iterator = readings.iterator
  }

  override def currentValue: Long = {
    if (iterator.hasNext) {
      lastValue = iterator.next()
    }
    lastValue
  }
}

object Smorgasbord extends EntityRecorderFactory[Smorgasbord] {
  override def category: String = "smörgåsbord"

  override def createRecorder(instrumentFactory: InstrumentFactory): Smorgasbord = new Smorgasbord(instrumentFactory)
}
