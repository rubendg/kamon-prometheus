package com.monsanto.arch.kamon.prometheus.converter

import com.monsanto.arch.kamon.prometheus.PrometheusGen
import com.monsanto.arch.kamon.prometheus.metric.{Metric, MetricFamily}
import org.scalacheck.Gen
import org.scalatest.prop.GeneratorDrivenPropertyChecks
import org.scalatest.{Matchers, WordSpec}

/** A suite of tests for the mungers.
  *
  * @author Daniel Solano Gómez
  */
class MungersSpec extends WordSpec with GeneratorDrivenPropertyChecks with Matchers {
  "the Prometheus metric name munger" should {
    import Mungers.asMetricName

    "handle the empty string" in {
      assert(!MetricFamily.isValidMetricFamilyName(""))
      asMetricName("") shouldBe "_"
    }

    "not do anything to valid Prometheus metric names" in {
      forAll(PrometheusGen.metricName → "name") { (name: String) ⇒
        assert(MetricFamily.isValidMetricFamilyName(name))
        asMetricName(name) shouldBe name
      }
    }

    "convert initial numbers to underscores" in {
      forAll(Gen.numChar → "initial digit", Gen.identifier → "rest of name") { (digit: Char, rest: String) ⇒
        val name = digit + rest
        val munged = '_' + rest

        assert(!MetricFamily.isValidMetricFamilyName(name))
        asMetricName(name) shouldBe munged
      }
    }

    "convert invalid characters to underscores" in {
      def isValidMetricNameChar(c: Char) = c match {
        case x if 'a'.to('z').contains(x) ⇒ true
        case x if 'A'.to('Z').contains(x) ⇒ true
        case x if '0'.to('9').contains(x) ⇒ true
        case ':' ⇒ true
        case '_' ⇒ true
        case _ ⇒ false
      }

      val invalidPrometheusMetricNameChar = PrometheusGen.unicodeChar.suchThat(!isValidMetricNameChar(_))

      val nameWithInvalidChars =
        Gen.nonEmptyListOf(Gen.frequency(9 → Gen.alphaChar, 1 → invalidPrometheusMetricNameChar))
          .map(_.mkString)
          .suchThat(str ⇒ !str.isEmpty && !str.charAt(0).isDigit && str.exists(!isValidMetricNameChar(_)))

      forAll(nameWithInvalidChars) { (name: String) ⇒
        val mungedName = name.map {
          case x if isValidMetricNameChar(x) ⇒ x
          case _ ⇒ '_'
        }.mkString
        assert(!MetricFamily.isValidMetricFamilyName(name))
        asMetricName(name) shouldBe mungedName
      }
    }
  }

  "the Prometheus label name munger" should {
    import Mungers.asLabelName

    "not do anything to valid Prometheus label names" in {
      forAll(PrometheusGen.labelName → "name") { (name: String) ⇒
        assert(Metric.isValidLabelName(name))
        asLabelName(name) shouldBe name
      }
    }

    "handle an empty label name" in {
      assert(!Metric.isValidLabelName(""))
      asLabelName("") shouldBe "_"
    }

    "convert leading digits into an underscore" in {
      forAll(Gen.numChar → "initial digit", Gen.identifier → "rest of name") { (digit: Char, rest: String) ⇒
        val name = digit + rest
        val munged = '_' + rest

        assert(!Metric.isValidLabelName(name))
        asLabelName(name) shouldBe munged
      }
    }

    "convert invalid characters to underscores" in {
      def isValidChar(c: Char) = c match {
        case x if 'a'.to('z').contains(x) ⇒ true
        case x if 'A'.to('Z').contains(x) ⇒ true
        case x if '0'.to('9').contains(x) ⇒ true
        case '_' ⇒ true
        case _ ⇒ false
      }

      val invalidNameChar = PrometheusGen.unicodeChar.suchThat(!isValidChar(_))

      val nameWithInvalidChars =
        Gen.nonEmptyListOf(Gen.frequency(9 → Gen.alphaChar, 1 → invalidNameChar))
          .map(_.mkString)
          .suchThat(
            str ⇒ !str.isEmpty
              && !str.charAt(0).isDigit
              && str.exists(!isValidChar(_))
              && (str.length > 2 && str.substring(0,2).exists(isValidChar)))

      forAll(nameWithInvalidChars → "name") { (name: String) ⇒
        val mungedName = name.map {
          case x if isValidChar(x) ⇒ x
          case _ ⇒ '_'
        }.mkString
        assert(!Metric.isValidLabelName(name))
        asLabelName(name) shouldBe mungedName
      }
    }

    "collapses multiple leading underscores" in {
      def isConvertibleToUnderscore(c: Char) = c match {
        case x if 'a'.to('z').contains(x) ⇒ false
        case x if 'A'.to('Z').contains(x) ⇒ false
        case x if '0'.to('9').contains(x) ⇒ false
        case _ ⇒ true
      }

      val underscoreChar = PrometheusGen.unicodeChar.suchThat(isConvertibleToUnderscore)

      val prefix = for {
        size ← Gen.posNum[Int].suchThat(_ >= 2)
        chars ← Gen.listOfN(size, underscoreChar)
      } yield chars.mkString

      forAll(prefix → "prefix", Gen.identifier → "rest of name") { (prefix: String, rest: String) ⇒
        val name = prefix + rest
        val mungedName = "_" + rest

        assert(!Metric.isValidLabelName(name))
        asLabelName(name) shouldBe mungedName
      }
    }
  }
}
