package com.monsanto.arch.kamon.prometheus.akka_http

import akka.http.scaladsl.coding.Gzip
import akka.http.scaladsl.model.headers.{Accept, HttpEncodings, `Accept-Encoding`}
import akka.http.scaladsl.model.{HttpCharsets, HttpResponse, StatusCodes}
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.http.scaladsl.unmarshalling.{FromResponseUnmarshaller, Unmarshaller}
import akka.stream.Materializer
import com.monsanto.arch.kamon.prometheus.akka_http.DefaultAkkaHttpEndpoint.MediaTypes._
import com.monsanto.arch.kamon.prometheus.converter.SnapshotConverter.{KamonCategoryLabel, KamonNameLabel}
import com.monsanto.arch.kamon.prometheus.metric._
import kamon.metric.SingleInstrumentEntityRecorder
import kamon.util.MilliTimestamp
import org.scalactic.Uniformity
import org.scalatest.FreeSpec
import org.scalatest.Matchers._

import scala.collection.immutable.ListMap
import scala.concurrent.{ExecutionContext, Future}

class AkkaHttpEndpointSpec extends FreeSpec with ScalatestRouteTest {
  val extension = AkkaHttpEndpoint(system).asInstanceOf[DefaultAkkaHttpEndpoint]

  val endpoint = extension.route

  private val textUnmarshaller =
    Unmarshaller.stringUnmarshaller
      .forContentTypes(`text/plain;version=0.0.4`)
      .map(TextFormat.parse)

  private val protobufUnmarshaller =
    Unmarshaller.byteArrayUnmarshaller
        .forContentTypes(`application/vnd.google.protobuf`)
        .map(ProtoBufFormat.parse)

  private val identityUnmarshaller =
    Unmarshaller.firstOf(textUnmarshaller, protobufUnmarshaller)

  private implicit val mainUnmarshaller =
    new FromResponseUnmarshaller[Seq[MetricFamily]] {
      override def apply(response: HttpResponse)
                        (implicit ec: ExecutionContext, materializer: Materializer): Future[Seq[MetricFamily]] = {
        response.encoding match {
          case HttpEncodings.identity ⇒ identityUnmarshaller(response.entity)
          case HttpEncodings.gzip     ⇒ identityUnmarshaller(Gzip.decode(response).entity)
        }
      }
    }

  "the AkkHttpEndpoint extension" - {
    "must provide a route" in {
      endpoint should not be null
    }

    "when fulfilling a plain-text request, " - {
      def doGet() = Get() ~> endpoint

      "and there is no content, it should return an empty response" in {
        doGet() ~> check {
          handled shouldBe true
          status shouldBe StatusCodes.NoContent
        }
      }

      "and it has content, it should" - {
        "handle GET requests" in withSampleSnapshot {
          doGet() ~> check {
            handled shouldBe true
            status shouldBe StatusCodes.OK
          }
        }

        "use the correct encoding" in withSampleSnapshot {
          doGet() ~> check {
            contentType.charsetOption should contain (HttpCharsets.`UTF-8`)
          }
        }

        "use the correct media type" in withSampleSnapshot {
          doGet() ~> check {
            mediaType shouldBe `text/plain;version=0.0.4`
          }
        }

        "is not compressed" in withSampleSnapshot {
          doGet() ~> check {
            response.encoding shouldBe HttpEncodings.identity
          }
        }

        "have the correct content" in withSampleSnapshot {
          doGet() ~> check {
            val response = responseAs[Seq[MetricFamily]]
            (response should contain theSameElementsAs sampleSnapshot) (after being normalised)
          }
        }
      }

      "accepting gzip compression" - {
        def doGet(): RouteTestResult =
          Get() ~> `Accept-Encoding`(HttpEncodings.gzip) ~> endpoint

        "and there is no content, it should return an empty response" in {
          doGet() ~> check {
            handled shouldBe true
            status shouldBe StatusCodes.NoContent
          }
        }

        "and there is content, it should" - {
          "handle GET requests" in withSampleSnapshot {
            doGet() ~> check {
              handled shouldBe true
              status shouldBe StatusCodes.OK
            }
          }

          "use the correct encoding" in withSampleSnapshot {
            doGet() ~> check {
              contentType.charsetOption should contain (HttpCharsets.`UTF-8`)
            }
          }

          "use the correct media type" in withSampleSnapshot {
            doGet() ~> check {
              mediaType shouldBe `text/plain;version=0.0.4`
            }
          }

          "be compressed" in withSampleSnapshot {
            doGet() ~> check {
              response.encoding shouldBe HttpEncodings.gzip
            }
          }

          "have the correct content" in withSampleSnapshot {
            doGet() ~> check {
              val response = responseAs[Seq[MetricFamily]]
              (response should contain theSameElementsAs sampleSnapshot) (after being normalised)
            }
          }
        }
      }
    }

    "when fulfilling a protocol buffer request" - {
      def doGet(): RouteTestResult =
        Get() ~> Accept(`application/vnd.google.protobuf`) ~> endpoint

      "and there is no content, it should return an empty response" in {
        doGet() ~> check {
          handled shouldBe true
          status shouldBe StatusCodes.NoContent
        }
      }

      "and there is content, it should" - {
        "handle GET requests" in withSampleSnapshot {
          doGet() ~> check {
            handled shouldBe true
            status shouldBe StatusCodes.OK
          }
        }

        "use the correct media type" in withSampleSnapshot {
          doGet() ~> check {
            mediaType shouldBe `application/vnd.google.protobuf`
          }
        }

        "not be compressed" in withSampleSnapshot {
          doGet() ~> check {
            response.encoding shouldBe HttpEncodings.identity
          }
        }

        "have the correct content" in withSampleSnapshot { snapshot: Seq[MetricFamily] ⇒
          doGet() ~> check {
            val response = responseAs[Seq[MetricFamily]]
            (response should contain theSameElementsAs snapshot) (after being normalised)
          }
        }
      }

      "accepting gzip compression" - {
        def doGet(): RouteTestResult =
          Get() ~>
              `Accept-Encoding`(HttpEncodings.gzip) ~>
              Accept(`application/vnd.google.protobuf`) ~>
              endpoint

        "and there is no content, it should return an empty response" in {
          doGet() ~> check {
            handled shouldBe true
            status shouldBe StatusCodes.NoContent
          }
        }

        "and there is content, it should" - {
          "handle GET requests" in withSampleSnapshot {
            doGet() ~> check {
              handled shouldBe true
              status shouldBe StatusCodes.OK
            }
          }

          "use the correct media type" in withSampleSnapshot {
            doGet() ~> check {
              mediaType shouldBe `application/vnd.google.protobuf`
            }
          }

          "be compressed" in withSampleSnapshot {
            doGet() ~> check {
              response.encoding shouldBe HttpEncodings.gzip
            }
          }

          "have the correct content" in withSampleSnapshot { snapshot: Seq[MetricFamily] ⇒
            doGet() ~> check {
              val response = responseAs[Seq[MetricFamily]]
              (response should contain theSameElementsAs snapshot) (after being normalised)
            }
          }
        }
      }
    }
  }

  /** A sample snapshot useful for testing. */
  val sampleSnapshot = {
    import MetricValue.{Bucket ⇒ B, Histogram ⇒ HG}

    val now = MilliTimestamp.now
    val ∞ = Double.PositiveInfinity
    Seq(
      MetricFamily("test_counter", PrometheusType.Counter, None,
        Seq(
          Metric(MetricValue.Counter(1), now,
            Map("type" → "a",
              KamonCategoryLabel → SingleInstrumentEntityRecorder.Counter,
              KamonNameLabel → "test_counter")),
          Metric(MetricValue.Counter(2), now,
            Map("type" → "b",
              KamonCategoryLabel → SingleInstrumentEntityRecorder.Counter,
              KamonNameLabel → "test_counter")))),
      MetricFamily("another_counter", PrometheusType.Counter, None,
        Seq(Metric(MetricValue.Counter(42), now,
          Map(KamonCategoryLabel → SingleInstrumentEntityRecorder.Counter, KamonNameLabel → "another_counter")))),
      MetricFamily("a_histogram", PrometheusType.Histogram, None,
        Seq(
          Metric(HG(Seq(B(1, 20), B(4, 23), B(∞, 23)), 23, 32), now,
            Map("got_label" → "yes",
              KamonCategoryLabel → SingleInstrumentEntityRecorder.Histogram,
              KamonNameLabel → "a_histogram")),
          Metric(HG(Seq(B(3, 2), B(5, 6), B(∞, 6)), 6, 26), now,
            Map("got_label" → "true",
              KamonCategoryLabel → SingleInstrumentEntityRecorder.Histogram,
              KamonNameLabel → "a_histogram")))),
      MetricFamily("another_histogram", PrometheusType.Histogram, None,
        Seq(Metric(HG(Seq(B(20, 20), B(∞, 20)), 20, 400), now,
          Map(KamonCategoryLabel → SingleInstrumentEntityRecorder.Histogram, KamonNameLabel → "another_histogram")))),
      MetricFamily("a_min_max_counter", PrometheusType.Histogram, None,
        Seq(Metric(HG(Seq(B(0, 1), B(1, 2), B(3, 3), B(∞, 3)), 3, 4), now,
          Map(
            KamonCategoryLabel → SingleInstrumentEntityRecorder.MinMaxCounter,
            KamonNameLabel → "a_min_max_counter")))))
  }

  /** Fixture that sets up the sample snapshot in the Spray endpoint extension. */
  def withSampleSnapshot[T](test: ⇒ T): T = {
    try {
      extension.snapshot.set(sampleSnapshot)
      test
    } finally {
      extension.snapshot.set(null)
    }
  }

  /** Normalises a metric family by ensuring its metrics are given an order and their timestamps are all given the
    * same value.
    */
  val normalised = new Uniformity[MetricFamily] {
    /** Sorts metrics according to their labels.  Assumes the labels are sorted. */
    def metricSort(a: Metric, b: Metric): Boolean = {
      (a.labels.headOption, b.labels.headOption) match {
        case (Some(x), Some(y)) ⇒
          if (x._1 < y._1) {
            true
          } else if (x._1 == y._1) {
            x._2 < y._2
          } else {
            false
          }
        case (None, Some(_)) ⇒ true
        case (Some(_), None) ⇒ false
        case (None, None) ⇒ false
      }
    }

    override def normalizedOrSame(b: Any): Any = b match {
      case mf: MetricFamily ⇒ normalized(mf)
      case _ ⇒ b
    }

    override def normalizedCanHandle(b: Any): Boolean = b.isInstanceOf[MetricFamily]

    override def normalized(metricFamily: MetricFamily): MetricFamily = {
      val normalMetrics = metricFamily.metrics.map { m ⇒
        val sortedLabels = ListMap(m.labels.toSeq.sortWith(_._1 < _._2): _*)
        Metric(m.value, new MilliTimestamp(0), sortedLabels)
      }.sortWith(metricSort)
      MetricFamily(metricFamily.name, metricFamily.prometheusType, metricFamily.help, normalMetrics)
    }
  }
}
