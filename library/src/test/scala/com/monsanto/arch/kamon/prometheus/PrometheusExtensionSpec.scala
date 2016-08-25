package com.monsanto.arch.kamon.prometheus

import akka.actor.ActorSystem
import com.monsanto.arch.kamon.prometheus.converter.SnapshotConverter
import com.monsanto.arch.kamon.prometheus.metric._
import kamon.Kamon
import kamon.metric.SingleInstrumentEntityRecorder
import kamon.util.MilliTimestamp
import org.scalactic.Uniformity
import org.scalatest.concurrent.Eventually.eventually
import org.scalatest.{Matchers, Outcome, fixture}
import spray.http.HttpHeaders.{Accept, `Accept-Encoding`}
import spray.http.{HttpEncodings, HttpResponse, MediaType, StatusCodes}
import spray.httpx.encoding.Gzip
import spray.httpx.unmarshalling.{Deserialized, FromResponseUnmarshaller, Unmarshaller}
import spray.testkit.ScalatestRouteTest

import scala.collection.immutable.ListMap
import scala.concurrent.Await
import scala.concurrent.duration.DurationInt

/** Tests the end-to-end functionality of the
  * [[com.monsanto.arch.kamon.prometheus.PrometheusExtension PrometheusExtension]].
  *
  * @author Daniel Solano Gómez
  */
class PrometheusExtensionSpec extends fixture.WordSpec with ScalatestRouteTest with Matchers {
  /** Unmarshaller from the text format. */
  val textUnmarshaller =
    Unmarshaller.delegate[String, Seq[MetricFamily]](PrometheusEndpoint.TextMediaType)(TextFormat.parse)
  /** Unmarshaller from the protocol buffer format. */
  val protoBufUnmarshaller =
    Unmarshaller.delegate[Array[Byte], Seq[MetricFamily]](PrometheusEndpoint.ProtoBufMediaType)(ProtoBufFormat.parse)
  /** Unmarshaller that supports both formats. */
  val plainUnmarshaller = Unmarshaller.oneOf(textUnmarshaller, protoBufUnmarshaller)
  /** Unmarshaller that supports both formats, including the gzipped versions. */
  implicit val mainUnmarshaller = new FromResponseUnmarshaller[Seq[MetricFamily]] {
    override def apply(response: HttpResponse): Deserialized[Seq[MetricFamily]] = {
      response.encoding match {
        case HttpEncodings.identity ⇒ plainUnmarshaller(response.entity)
        case HttpEncodings.gzip ⇒ plainUnmarshaller(Gzip.decode(response).entity)
      }
    }
  }

  override type FixtureParam = PrometheusExtension

  /** Starts and stops Kamon around each test. */
  override def withFixture(test: OneArgTest): Outcome = {
    Kamon.start()
    val system = ActorSystem()
    val extension = Prometheus(system)
    try {
      withFixture(test.toNoArgTest(extension))
    } finally {
      Await.result(system.terminate(), 1.second)
    }
  }

  "The Prometheus extension" should {
    "be available from Kamon" in { extension ⇒
      extension should not be null
    }

    "provide a spray endpoint" in { extension ⇒
      extension.route should not be null
    }

    "not buffer when the refresh interval is the same as the tick interval" in { extension ⇒
      extension.isBuffered shouldBe false
      extension.listener shouldBe theSameInstanceAs(extension.buffer)
    }
  }

  "The Prometheus extension endpoint" when {
    "it has no snapshots" should {
      "returns an empty response" in { kamonPrometheus ⇒
        Get() ~> kamonPrometheus.route ~> check {
          handled shouldBe true
          status shouldBe StatusCodes.NoContent
        }
      }
    }

    "doing a plain-text request" when {
      def doGet(extension: PrometheusExtension) = Get() ~> extension.route

      "it has content" should {
        "handle GET requests" in withData { (extension, _) ⇒
          doGet(extension) ~> check {
            handled shouldBe true
            status shouldBe StatusCodes.OK
          }
        }

        "use the correct encoding" in withData { (extension, _) ⇒
          doGet(extension) ~> check {
            definedCharset shouldBe defined
            charset.value shouldBe "UTF-8"
          }
        }

        "use the correct media type" in withData { (extension, _) ⇒
          doGet(extension) ~> check {
            mediaType shouldBe MediaType.custom("text", "plain", parameters = Map("version" -> "0.0.4"))
          }
        }

        "is not compressed" in withData { (extension, _) ⇒
          doGet(extension) ~> check {
            response.encoding shouldBe HttpEncodings.identity
          }
        }

        "have the correct content" in withData { (extension, snapshot) ⇒
          doGet(extension) ~> check {
            val response = responseAs[Seq[MetricFamily]]
            (response should contain theSameElementsAs snapshot) (after being normalised)
          }
        }
      }
    }

    "doing a plain-text request accepting gzip compression" when {
      def doGet(extension: PrometheusExtension) =
        Get() ~> `Accept-Encoding`(HttpEncodings.gzip) ~> extension.route

      "it has content" should {
        "handle GET requests" in withData { (extension, _) ⇒
          doGet(extension) ~> check {
            handled shouldBe true
            status shouldBe StatusCodes.OK
          }
        }

        "use the correct encoding" in withData { (extension, _) ⇒
          doGet(extension) ~> check {
            definedCharset shouldBe defined
            charset.value shouldBe "UTF-8"
          }
        }

        "use the correct media type" in withData { (extension, _) ⇒
          doGet(extension) ~> check {
            mediaType shouldBe MediaType.custom("text", "plain", parameters = Map("version" -> "0.0.4"))
          }
        }

        "is compressed" in withData { (extension, _) ⇒
          doGet(extension) ~> check {
            response.encoding shouldBe HttpEncodings.gzip
          }
        }

        "have the correct content" in withData { (extension, snapshot) ⇒
          doGet(extension) ~> check {
            val response = responseAs[Seq[MetricFamily]]
            (response should contain theSameElementsAs snapshot) (after being normalised)
          }
        }
      }
    }

    "doing a protocol buffer request" when {
      def doGet(extension: PrometheusExtension) =
        Get() ~> Accept(PrometheusEndpoint.ProtoBufMediaType) ~> extension.route

      "it has content" should {
        "handle GET requests" in withData { (extension, _) ⇒
          doGet(extension) ~> check {
            handled shouldBe true
            status shouldBe StatusCodes.OK
          }
        }

        "use the correct media type" in withData { (extension, _) ⇒
          doGet(extension) ~> check {
            mediaType shouldBe PrometheusEndpoint.ProtoBufMediaType
          }
        }

        "is not compressed" in withData { (extension, _) ⇒
          doGet(extension) ~> check {
            response.encoding shouldBe HttpEncodings.identity
          }
        }

        "have the correct content" in withData { (extension, snapshot) ⇒
          doGet(extension) ~> check {
            val response = responseAs[Seq[MetricFamily]]
            (response should contain theSameElementsAs snapshot) (after being normalised)
          }
        }
      }
    }

    "doing a protocol buffer request accepting gzip compression" when {
      def doGet(extension: PrometheusExtension) =
        Get() ~>
        `Accept-Encoding`(HttpEncodings.gzip) ~>
        Accept(PrometheusEndpoint.ProtoBufMediaType) ~>
        extension.route

      "it has content" should {
        "handle GET requests" in withData { (extension, _) ⇒
          doGet(extension) ~> check {
            handled shouldBe true
            status shouldBe StatusCodes.OK
          }
        }

        "use the correct media type" in withData { (extension, _) ⇒
          doGet(extension) ~> check {
            mediaType shouldBe PrometheusEndpoint.ProtoBufMediaType
          }
        }

        "is not compressed" in withData { (extension, _) ⇒
          doGet(extension) ~> check {
            response.encoding shouldBe HttpEncodings.gzip
          }
        }

        "have the correct content" in withData { (extension, snapshot) ⇒
          doGet(extension) ~> check {
            val response = responseAs[Seq[MetricFamily]]
            (response should contain theSameElementsAs snapshot) (after being normalised)
          }
        }
      }
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
    //noinspection ComparingUnrelatedTypes
    override def normalizedCanHandle(b: Any): Boolean = b.isInstanceOf[MetricFamily]

    override def normalized(metricFamily: MetricFamily): MetricFamily = {
      val normalMetrics = metricFamily.metrics.map { m ⇒
        val sortedLabels = ListMap(m.labels.toSeq.sortWith(_._1 < _._2): _*)
        Metric(m.value, new MilliTimestamp(0), sortedLabels)
      }.sortWith(metricSort)
      MetricFamily(metricFamily.name, metricFamily.prometheusType, metricFamily.help, normalMetrics)
    }
  }

  def withData[T](test: (PrometheusExtension, Seq[MetricFamily]) ⇒ T): PrometheusExtension ⇒ T = { extension ⇒
    try {
      import SnapshotConverter.{KamonCategoryLabel, KamonNameLabel}
      val ts = MilliTimestamp.now
      import MetricValue.{Bucket ⇒ B, Histogram ⇒ HG}
      val ∞ = Double.PositiveInfinity
      val snapshot = Seq(
        MetricFamily("test_counter", PrometheusType.Counter, None,
          Seq(
            Metric(MetricValue.Counter(1), ts,
              Map("type" → "a",
                KamonCategoryLabel → SingleInstrumentEntityRecorder.Counter,
                KamonNameLabel → "test_counter")),
            Metric(MetricValue.Counter(2), ts,
              Map("type" → "b",
                KamonCategoryLabel → SingleInstrumentEntityRecorder.Counter,
                KamonNameLabel → "test_counter")))),
        MetricFamily("another_counter", PrometheusType.Counter, None,
          Seq(Metric(MetricValue.Counter(42), ts,
            Map(KamonCategoryLabel → SingleInstrumentEntityRecorder.Counter, KamonNameLabel → "another_counter")))),
        MetricFamily("a_histogram", PrometheusType.Histogram, None,
          Seq(
            Metric(HG(Seq(B(1, 20), B(4, 23), B(∞, 23)), 23, 32), ts,
              Map("got_label" → "yes",
                KamonCategoryLabel → SingleInstrumentEntityRecorder.Histogram,
                KamonNameLabel → "a_histogram")),
            Metric(HG(Seq(B(3, 2), B(5, 6), B(∞, 6)), 6, 26), ts,
              Map("got_label" → "true",
                KamonCategoryLabel → SingleInstrumentEntityRecorder.Histogram,
                KamonNameLabel → "a_histogram")))),
        MetricFamily("another_histogram", PrometheusType.Histogram, None,
          Seq(Metric(HG(Seq(B(20, 20), B(∞, 20)), 20, 400), ts,
            Map(KamonCategoryLabel → SingleInstrumentEntityRecorder.Histogram, KamonNameLabel → "another_histogram")))),
        MetricFamily("a_min_max_counter", PrometheusType.Histogram, None,
          Seq(Metric(HG(Seq(B(0, 1), B(1, 2), B(3, 3), B(∞, 3)), 3, 4), ts,
            Map(
              KamonCategoryLabel → SingleInstrumentEntityRecorder.MinMaxCounter,
              KamonNameLabel → "a_min_max_counter")))))

      Kamon.metrics.counter("test_counter", Map("type" -> "a")).increment()
      Kamon.metrics.counter("test_counter", Map("type" -> "b")).increment(2)
      Kamon.metrics.counter("another_counter").increment(42)
      val h = Kamon.metrics.histogram("a_histogram", Map("got_label" → "true"))
      h.record(3, 2)
      h.record(5, 4)
      val h2 = Kamon.metrics.histogram("a_histogram", Map("got_label" → "yes"))
      h2.record(1, 20)
      h2.record(4, 3)
      val h3 = Kamon.metrics.histogram("another_histogram")
      h3.record(20, 20)
      val mmc = Kamon.metrics.minMaxCounter("a_min_max_counter")
      mmc.increment(3)
      mmc.decrement(2)
      KamonTestKit.flushSubscriptions()
      eventually {
        Option(extension.endpoint.snapshot.get()) shouldBe defined
        test(extension, snapshot)
      }
    } finally KamonTestKit.clearEntities()
  }
}
