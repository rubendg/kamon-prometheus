package com.monsanto.arch.kamon.prometheus.metric

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, InputStream, OutputStream}

import com.monsanto.arch.kamon.prometheus.proto.Metrics
import kamon.util.MilliTimestamp

/** Provides support for reading/writing messages using Google’s protocol buffers.
  *
  * @author Daniel Solano Gómez
  */
object ProtoBufFormat extends SerialisationFormat[Array[Byte]] {
  /** Serialises the snapshot. */
  override def format(snapshot: Seq[MetricFamily]): Array[Byte] = {
    val out = new ByteArrayOutputStream
    try {
      writeTo(snapshot, out)
      out.toByteArray
    } finally out.close()
  }

  /** Constructs a snapshot from its serialised format. */
  override def parse(source: Array[Byte]): Seq[MetricFamily] = {
    val in = new ByteArrayInputStream(source)
    try {
      readFrom(in)
    } finally in.close()
  }

  /** Constructs a Kamon/Prometheus snapshot from an input stream that contains a snapshot serialised with the
    * Prometheus protocol buffer binary format.
    */
  private[metric] def readFrom(in: InputStream): Seq[MetricFamily] = {
    import scala.collection.JavaConversions.asScalaBuffer

    val builder = Seq.newBuilder[MetricFamily]
    while(in.available() > 0) {
      val pbFamily = Metrics.MetricFamily.parseDelimitedFrom(in)
      val name = pbFamily.getName
      val prometheusType = pbFamily.getType match {
        case Metrics.MetricType.COUNTER ⇒ PrometheusType.Counter
        case Metrics.MetricType.HISTOGRAM ⇒ PrometheusType.Histogram
        //noinspection NotImplementedCode
        case _ ⇒ ???
      }
      val help = if (pbFamily.hasHelp) Some(pbFamily.getHelp) else None
      val metrics: List[Metric] = pbFamily.getMetricList.map { pbMetric ⇒
        val timestamp = new MilliTimestamp(pbMetric.getTimestampMs)
        val labels: Map[String,String] = pbMetric.getLabelList.map(l ⇒ (l.getName, l.getValue)).toMap
        val value = prometheusType match {
          case PrometheusType.Counter ⇒
            assert(pbMetric.hasCounter)
            MetricValue.Counter(pbMetric.getCounter.getValue)
          case PrometheusType.Histogram ⇒
            assert(pbMetric.hasHistogram)
            val pbValue = pbMetric.getHistogram
            val buckets = {
              pbValue.getBucketList.map(b ⇒ MetricValue.Bucket(b.getUpperBound, b.getCumulativeCount)).toList
            }
            MetricValue.Histogram(buckets, pbValue.getSampleCount, pbValue.getSampleSum)
        }
        Metric(value, timestamp, labels)
      }.toList
      builder += MetricFamily(name, prometheusType,help, metrics)
    }
    builder.result()
  }

  /** Writes the given Kamon/Prometheus snapshot to the output stream using the Prometheus protocol buffer format. */
  private[metric] def writeTo(snapshot: Seq[MetricFamily], out: OutputStream) {
    snapshot.foreach { family ⇒
      val builder = Metrics.MetricFamily.newBuilder()
        .setName(family.name)
        .setType(family.prometheusType.prometheusType)
      family.help.foreach(builder.setHelp)

      family.metrics.foreach { metric ⇒
        import scala.collection.JavaConverters.asJavaIterableConverter

        val metricBuilder = Metrics.Metric.newBuilder()
          .setTimestampMs(metric.timestamp.millis)
          .addAllLabel {
            metric.labels.map(l ⇒ Metrics.LabelPair.newBuilder().setName(l._1).setValue(l._2).build()).asJava
          }
        metric.value match {
          case MetricValue.Counter(count) ⇒
            metricBuilder.setCounter(metricBuilder.getCounterBuilder.setValue(count))
          case MetricValue.Histogram(buckets, count, sum) ⇒
            metricBuilder.setHistogram {
              metricBuilder.getHistogramBuilder
                .setSampleCount(count)
                .setSampleSum(sum)
                .addAllBucket {
                  buckets.map { b ⇒
                    Metrics.Bucket.newBuilder()
                      .setUpperBound(b.upperBound)
                      .setCumulativeCount(b.cumulativeCount)
                      .build()
                  }.asJava
                }
                .build()
            }
        }
        builder.addMetric(metricBuilder)
      }

      builder.build().writeDelimitedTo(out)
    }
  }
}
