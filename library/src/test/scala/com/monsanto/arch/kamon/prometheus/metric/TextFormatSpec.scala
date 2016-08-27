package com.monsanto.arch.kamon.prometheus.metric

import com.monsanto.arch.kamon.prometheus.PrometheusGen
import com.monsanto.arch.kamon.prometheus.metric.TextFormat.ParseHelper.MetricLine
import kamon.util.MilliTimestamp
import org.scalacheck.Gen
import org.scalactic.Equality
import org.scalatest.prop.GeneratorDrivenPropertyChecks
import org.scalatest.{Matchers, WordSpec}

import scala.reflect.ClassTag

/** Test suite for the TextFormat.
  *
  * @author Daniel Solano Gómez
  */
class TextFormatSpec extends WordSpec with GeneratorDrivenPropertyChecks with Matchers {
  "The text format" should {
    "write a basic counter snapshot" in new CounterSerialisationFixture {
      TextFormat.format(snapshot) shouldBe snapshotString
    }

    "read a basic counter snapshot" in new CounterSerialisationFixture {
      TextFormat.parse(snapshotString) shouldBe snapshot
    }

    "write a basic histogram snapshot" in new HistogramSerialisationFixture {
      TextFormat.format(snapshot) shouldBe snapshotString
    }

    "read a basic histogram snapshot" in new HistogramSerialisationFixture {
      TextFormat.parse(snapshotString) shouldBe snapshot
    }

    "write a basic min-max counter snapshot" in new MinMaxCounterSerialisationFixture {
      TextFormat.format(snapshot) shouldBe snapshotString
    }

    "read a basic min-max counter snapshot" in new MinMaxCounterSerialisationFixture {
      TextFormat.parse(snapshotString) shouldBe snapshot
    }

    "write a basic gauge snapshot" in new GaugeSerialisationFixture {
      TextFormat.format(snapshot) shouldBe snapshotString
    }

    "read a basic gauge snapshot" in new GaugeSerialisationFixture {
      TextFormat.parse(snapshotString) shouldBe snapshot
    }

    "round-trip arbitrary snapshots" in {
      forAll(PrometheusGen.snapshot → "snapshot", sizeRange(25)) { (snapshot: Seq[MetricFamily]) ⇒
        TextFormat.parse(TextFormat.format(snapshot)) shouldBe snapshot
      }
    }
  }

  "The text help escaper" should {
    "escape backslashes" in {
      TextFormat.escapeHelp("\\") shouldBe "\\\\"
    }
    "escape newlines" in {
      TextFormat.escapeHelp("\n") shouldBe "\\n"
    }
    "escape a slash followed by an n properly" in {
      TextFormat.escapeHelp("\\n") shouldBe "\\\\n"
    }
    "not escape a double quote" in {
      TextFormat.escapeHelp("\"") shouldBe "\""
    }
  }

  "The text help unescaper" should {
    "unescape a backslash" in {
      TextFormat.unescapeHelp("\\\\") shouldBe "\\"
    }
    "unescape a newline" in {
      TextFormat.unescapeHelp("\\n") shouldBe "\n"
    }
    "unescape a backslash followed by an n" in {
      TextFormat.unescapeHelp("\\\\n") shouldBe "\\n"
    }
    "not unescape a double quote" in {
      intercept[IllegalArgumentException](TextFormat.unescapeHelp("""\""""))
    }
  }

  "The text help escape/unescape" should {
    "work with arbitrary help strings" in {
      forAll(PrometheusGen.help, minSuccessful(100)) {(help: String) ⇒
        TextFormat.unescapeHelp(TextFormat.escapeHelp(help)) shouldBe help
      }
    }
  }

  "The label value escaper" should {
    "escape backslashes" in {
      TextFormat.escapeLabelValue("""\""") shouldBe """\\"""
    }

    "escape newlines" in {
      TextFormat.escapeLabelValue("\n") shouldBe """\n"""
    }

    "escape double quotes" in {
      TextFormat.escapeLabelValue("\"") shouldBe """\""""
    }
  }

  "The label value unescaper" should {
    "unescape a backslash" in {
      TextFormat.unescapeLabelValue("\\\\") shouldBe "\\"
    }
    "unescape a newline" in {
      TextFormat.unescapeLabelValue("\\n") shouldBe "\n"
    }
    "unescape a double quote" in {
      TextFormat.unescapeLabelValue("\\\"") shouldBe "\""
    }
    "unescape a backslash followed by an n" in {
      TextFormat.unescapeLabelValue("\\\\n") shouldBe "\\n"
    }
  }

  val labelValue = {
    val escapeChars = Seq('\\', '"', '\n')
    Gen.listOf(
      Gen.frequency(
        9 → PrometheusGen.unicodeChar,
        1 → Gen.oneOf(escapeChars)
      )
    ).map(_.mkString).suchThat(s ⇒ s.isEmpty || s.exists(escapeChars.contains))
  }

  "The label value escape/unescape" should {
    "work with arbitrary strings strings" in {
      forAll(labelValue, minSuccessful(250)) {(value: String) ⇒
        TextFormat.unescapeLabelValue(TextFormat.escapeLabelValue(value)) shouldBe value
      }
    }
  }

  "The text format parse helper" should {
    import TextFormat.ParseHelper

    implicit def parseResultEq[T: ClassTag] = new Equality[ParseHelper.ParseResult[T]] {
      override def areEqual(result: ParseHelper.ParseResult[T], expected: Any) = {
        expected match {
          case v: T if result.successful ⇒
            result.get match {
              case x: Double ⇒ (x == v) || (x.isNaN && v.asInstanceOf[Double].isNaN)
              case x ⇒ x === v
            }
          case _ ⇒ false
        }
      }
    }

    def formatTags(tags: Map[String, String]): String = {
      if (tags.isEmpty) {
        ""
      } else {
        tags.map { label ⇒
          val (name, value) = label
          s"""$name="${TextFormat.escapeLabelValue(value)}""""
        }.mkString("{",",","}")
      }
    }

    "parse valid metric names" in {
      forAll(PrometheusGen.metricName → "name") { (name: String) ⇒
        val result = ParseHelper.parseAll(ParseHelper.metricFamilyName, name)
        result shouldEqual name
      }
    }

    "parse a help string" in {
      forAll(PrometheusGen.help → "help") { (help: String) ⇒
        val escapedHelp = TextFormat.escapeHelp(help)
        val result = ParseHelper.parseAll(ParseHelper.help, escapedHelp)
        result shouldEqual help
      }
    }

    "parse a help line" in {
      forAll(PrometheusGen.metricName → "name", PrometheusGen.help → "help") { (name: String, help: String) ⇒
        val line = s"# HELP $name ${TextFormat.escapeHelp(help)}"
        val result = ParseHelper.parseAll(ParseHelper.helpLine, line)
        result shouldEqual (name, help)
      }
    }

    "parse a Prometheus type" when {
      "it is a counter" in {
        val result = ParseHelper.parseAll(ParseHelper.prometheusType, PrometheusType.Counter.text)
        result shouldEqual PrometheusType.Counter
      }
    }

    "parse a prometheus type line" in {
      val prometheusTypeGen = Gen.const[PrometheusType](PrometheusType.Counter)

      forAll(
        PrometheusGen.metricName → "name",
        prometheusTypeGen → "prometheusType"
      ) { (name: String, prometheusType: PrometheusType) ⇒
        val line = s"# TYPE $name ${prometheusType.text}"
        val result = ParseHelper.parseAll(ParseHelper.typeLine, line)

        result shouldEqual (name, prometheusType)
      }
    }

    "parse a label name" in {
      forAll(PrometheusGen.labelName → "label name") { (name: String) ⇒
        val result = ParseHelper.parseAll(ParseHelper.labelName, name)
        result shouldEqual name
      }
    }

    "parse a label value" in {
      forAll(labelValue → "label value") { (value: String) ⇒
        val result = ParseHelper.parseAll(ParseHelper.labelValue, TextFormat.escapeLabelValue(value))
        result shouldEqual value
      }
    }

    "parse a label entry" in {
      forAll(PrometheusGen.labelName → "name", labelValue → "value") { (name: String, value: String) ⇒
        val formatted = s"""$name="${TextFormat.escapeLabelValue(value)}""""
        val result = ParseHelper.parseAll(ParseHelper.label, formatted)
        result shouldEqual (name, value)
      }
    }

    "parse a labels list" in {
      forAll(PrometheusGen.tags → "tags") {tags: Map[String,String] ⇒
        val formatted = formatTags(tags)
        val result = ParseHelper.parseAll(ParseHelper.labels, formatted)
        result shouldEqual tags
      }
    }

    "parse a value" in {
      forAll(PrometheusGen.arbitraryMetricValue → "value") { (value: Double) ⇒
        val result = ParseHelper.parseAll(ParseHelper.value, TextFormat.formatValue(value))
        result shouldEqual value
      }
    }

    "parse a timestamp" in {
      forAll(PrometheusGen.milliTimestamp → "timestamp") { (timestamp: MilliTimestamp) ⇒
        val formatted = timestamp.millis.toString
        val result = ParseHelper.parseAll(ParseHelper.timestamp, formatted)
        result shouldEqual timestamp
      }
    }

    "parse a metric line" in {
      forAll(
        PrometheusGen.metricName → "name",
        PrometheusGen.tags → "tags",
        PrometheusGen.arbitraryMetricValue → "value",
        PrometheusGen.milliTimestamp → "timestamp"
      ) { (name: String, tags: Map[String,String], value: Double, timestamp: MilliTimestamp) ⇒
        val line = s"$name${formatTags(tags)} ${TextFormat.formatValue(value)} ${timestamp.millis}"
        val result = ParseHelper.parseAll(ParseHelper.metricLine, line)
        result shouldEqual MetricLine(name, tags, value, timestamp)
      }
    }

    "parse a basic metric" in {
      val lines =
        s"""# HELP some_metric Help me!
           |# TYPE some_metric counter
           |some_metric{tag_1="Hello"} 3 1000
        """.stripMargin

      val result = ParseHelper.parseAll(ParseHelper.metricFamily, lines)
      result shouldEqual MetricFamily("some_metric", PrometheusType.Counter, Some("Help me!"),
        Seq(Metric(MetricValue.Counter(3), new MilliTimestamp(1000), Map("tag_1" → "Hello"))))
    }

    "parse a simple snapshot" in {
      val lines =
        s"""# HELP some_metric Help me!
           |# TYPE some_metric counter
           |some_metric{tag_1="Hello"} 3 1000
           |
           |# TYPE another_metric counter
           |another_metric 42 1000
        """.stripMargin

      val result = ParseHelper.parseAll(ParseHelper.snapshot, lines)
      result shouldEqual
        Seq(
          MetricFamily("some_metric", PrometheusType.Counter, Some("Help me!"),
            Seq(Metric(MetricValue.Counter(3), new MilliTimestamp(1000), Map("tag_1" → "Hello")))),
          MetricFamily("another_metric", PrometheusType.Counter, None,
            Seq(Metric(MetricValue.Counter(42), new MilliTimestamp(1000)))))
    }
  }
}
