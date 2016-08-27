package com.monsanto.arch.kamon.prometheus.metric

import kamon.util.MilliTimestamp

import scala.collection.immutable.ListMap
import scala.collection.mutable.ListBuffer
import scala.util.parsing.combinator.JavaTokenParsers

/** Support for serialising to/from the Prometheus text format.
  *
  * @author Daniel Solano Gómez
  */
object TextFormat extends SerialisationFormat[String] {
  /** Serialises the snapshot. */
  override def format(snapshot: Seq[MetricFamily]): String = {
    snapshot.map { family ⇒
      val metrics = family.metrics.flatMap { metric ⇒
        metric.value match {
          case MetricValue.Counter(c) ⇒ Seq(formatLine(family.name, metric.labels, c, metric.timestamp))
          case MetricValue.Histogram(buckets, count, sum) ⇒
            buckets.map { bucket ⇒
              formatLine(family.name, metric.labels + ("le" → formatValue(bucket.upperBound)), bucket.cumulativeCount,
                metric.timestamp)
            } ++ Seq(
              formatLine(family.name + "_count", metric.labels, count, metric.timestamp),
              formatLine(family.name + "_sum", metric.labels, sum, metric.timestamp)
            )
        }
      }.mkString("\n")
      val helpString = family.help.map(h ⇒ s"# HELP ${family.name} ${escapeHelp(h)}\n").getOrElse("")
      s"""$helpString# TYPE ${family.name} ${family.prometheusType.text}
         |$metrics
         |""".stripMargin
    }.mkString("\n")
  }

  /** Formats a metric line as expected by Prometheus. */
  private def formatLine(name: String, labels: Map[String,String], value: Double, timestamp: MilliTimestamp): String = {
    val labelStr = {
      if (labels.isEmpty) ""
      else labels.map(label ⇒ s"""${label._1}="${escapeLabelValue(label._2)}"""").mkString("{", ",", "}")
    }
    s"$name$labelStr ${formatValue(value)} ${timestamp.millis}"
  }

  /** Constructs a snapshot from its serialised format. */
  override def parse(source: String): Seq[MetricFamily] = {
    ParseHelper.parseAll(ParseHelper.snapshot, source) match {
      case ParseHelper.Success(snapshot, _) ⇒ snapshot
      case ParseHelper.Failure(msg, _) ⇒ throw new RuntimeException(s"Failed to parse snapshot: $msg")
      case ParseHelper.Error(msg, _) ⇒ throw new RuntimeException(s"Failed to parse snapshot: $msg")
    }
  }

  /** Escapes all disallowed characters from help strings.  In particular, these are backslashes and new lines. */
  private[metric] def escapeHelp(help: String) = {
    help.replace("\\", "\\\\").replace("\n", "\\n")
  }

  /** Unescapes all allowed escape sequences from help strings. */
  private[metric] def unescapeHelp(escapedHelp: String) = unescape(escapedHelp, "help string", escapeDoubleQuotes = false)

  /** Escapes all disallowed characters from label values.  In particular, these are backslashes, double quotes, and
    * new lines.
    */
  private[metric] def escapeLabelValue(value: String) = {
    value.replace( """\""", """\\""").replace("\n", """\n""").replace("\"", """\"""")
  }

  /** Unescapes backslashes, double quotes, and new lines from label values. */
  private[metric] def unescapeLabelValue(escapedValue: String) = unescape(escapedValue, "label value", escapeDoubleQuotes = true)

  /** Somewhat general unescape routine.  In particular, this is useful so that input text like '\\n' does not get
    * unintentionally unescaped.
    */
  private def unescape(str: String, description: String, escapeDoubleQuotes: Boolean): String = {
    val stringBuilder = new StringBuilder
    val i = str.iterator
    while (i.hasNext) {
      i.next() match {
        case '\\' ⇒ {
          val escapedChar = if (i.hasNext) Some(i.next()) else None
          escapedChar match {
            case Some('n') ⇒ stringBuilder.append('\n')
            case Some('\\') ⇒ stringBuilder.append('\\')
            case Some('"') if escapeDoubleQuotes ⇒ stringBuilder.append('"')
            case Some(c) ⇒
              throw new IllegalArgumentException(s"This is not a properly escaped $description, it contains the " +
                s"illegal escape sequence '\\$c'")
            case None ⇒
              throw new IllegalArgumentException(s"This is not a properly escaped $description, it ends with an " +
                "unterminated escape sequence")
          }
        }
        case x ⇒ stringBuilder.append(x)
      }
    }
    stringBuilder.toString()
  }

  /** Formats a metric value using Prometheus’ notation. */
  private[metric] def formatValue(d: Double): String = d match {
    case Double.PositiveInfinity ⇒ "+Inf"
    case Double.NegativeInfinity ⇒ "-Inf"
    case x if x.isNaN ⇒ "Nan"
    case _ ⇒ d.toString
  }

  /** Parses a metric value that has been written out using Prometheus’ notation. */
  private def parseValue(s: String): Double = s match {
    case "+Inf" ⇒ Double.PositiveInfinity
    case "-Inf" ⇒ Double.NegativeInfinity
    case "Nan" ⇒ Double.NaN
    case _ ⇒ s.toDouble
  }

  /** Parsers for the Prometheus text exposition format.  Note that these parsers are slightly stricter than what
    * Prometheus supports; they support what Kamon/Prometheus will generate.
    */
  private[metric] object ParseHelper extends JavaTokenParsers {
    /** Parses a Prometheus metric family name. */
    def metricFamilyName: Parser[String] = "[a-zA-Z_:][a-zA-Z0-9_:]*".r

    /** Parses the help text for a metric and unescapes it. */
    def help: Parser[String] = """[^\n]*""".r ^^ unescapeHelp

    /** Parses a line of help text. */
    def helpLine: Parser[(String, String)] = "# HELP" ~> metricFamilyName ~ help ^^ { case n ~ h ⇒ n → h }

    /** Parses the textual representation of a Prometheus type to an object. */
    def prometheusType: Parser[PrometheusType] = {
      def counter: Parser[PrometheusType] = PrometheusType.Counter.text ^^^ PrometheusType.Counter
      def histogram: Parser[PrometheusType] = PrometheusType.Histogram.text ^^^ PrometheusType.Histogram
      counter | histogram
    }

    /** Parses a type indicator line. */
    def typeLine: Parser[(String, PrometheusType)] = "# TYPE" ~> metricFamilyName ~ prometheusType ^^ {
      case n ~ t ⇒ n → t
    }

    /** Matches a Prometheus label name. */
    def labelName: Parser[String] = """[a-zA-Z_][a-zA-Z0-9_]*""".r

    /** Matches a Prometheus label value and unescapes it.  Note that we use a custom parser here to not eat
      * Whitespace at the beginning.
      */
    def labelValue: Parser[String] = new Parser[String] {
      override def apply(in: Input): ParseResult[String] = {
        val source = in.source
        val offset = in.offset
        val labelValueRegex = """([^"\\]|\\[\\"n])*""".r
        labelValueRegex.findPrefixMatchOf(source.subSequence(offset, source.length)) match {
          case Some(matched) ⇒
            Success(unescapeLabelValue(source.subSequence(offset, offset + matched.end).toString),
              in.drop(matched.end))
          case None ⇒
            val found = if (offset == source.length()) "end of source" else s"'${source.charAt(offset)}'"
            Failure(s"string matching regex ‘$labelValueRegex‘ expected but $found found", in)
        }
      }
    }

    /** Parses a single label name/value. */
    def label: Parser[(String, String)] = ( labelName <~ "=\"" ) ~ ( labelValue <~ "\"" ) ^^ {case n ~ v ⇒ (n, v)}

    /** Parses all of the labels for a particular metric. */
    def labels: Parser[Map[String,String]] =
      opt("{" ~> repsep(label, ",")<~ "}") ^^ (_.map(_.toMap).getOrElse(Map.empty[String,String]))

    /** Parses a metric value. */
    def value: Parser[Double] = ( "Nan" | "+Inf" | "-Inf" | floatingPointNumber) ^^ parseValue

    /** Parses a timestamp. */
    def timestamp: Parser[MilliTimestamp] = wholeNumber ^^ (n ⇒ new MilliTimestamp(n.toLong))

    /** Represents a metric measurement line.  Overrides equality for the purpose of testing. */
    case class MetricLine(name: String, labels: Map[String,String], value: Double, timestamp: MilliTimestamp) {
      override def equals(x: Any): Boolean = {
        x match {
          case MetricLine(n, l, v, ts) ⇒
            n == name && l == labels && ts == timestamp && (v == value || (v.isNaN && value.isNaN))
          case _ ⇒ false
        }
      }
    }

    /** Parses a line with a metric value. */
    def metricLine: Parser[MetricLine] = {
      metricFamilyName ~ labels ~ value ~ timestamp ^^ { case n ~ l ~ v ~ ts ⇒ MetricLine(n, l, v, ts) }
    }

    /** Parses a metric family entry. */
    def metricFamily: Parser[MetricFamily] = opt(helpLine) ~ typeLine ~ rep(metricLine) ^^ {
      case Some((name1, help)) ~ ((name2, prometheusType)) ~ metrics ⇒ {
        if (name1 != name2) {
          throw new IllegalArgumentException(s"The help line used the name $name1, which does not match the name on " +
            s"the type line $name2")
        }
        evaluateMetricFamily(name1, prometheusType, Some(help), metrics)
      }
      case None ~ ((name, prometheusType)) ~ metrics ⇒ evaluateMetricFamily(name, prometheusType, None, metrics)
    }

    private def evaluateMetricFamily(name: String, prometheusType: PrometheusType, help: Option[String],
                                     metricLines: Seq[MetricLine]): MetricFamily = {
      val metrics = prometheusType match {
        case PrometheusType.Counter ⇒
          require(metricLines.forall(_.name == name), s"Metric lines for the counter $name must have the same name.")
          metricLines.map(line ⇒ Metric(MetricValue.Counter(line.value), line.timestamp, line.labels))
        case PrometheusType.Histogram ⇒
          val histogramGroups = {
            metricLines.foldLeft(ListMap.empty[Map[String,String], ListBuffer[MetricLine]]) { (map, line) ⇒
              val bareLabels = line.labels - "le"
              map.get(bareLabels) match {
                case Some(list) ⇒
                  list += line
                  map
                case None ⇒
                  map + (bareLabels → ListBuffer(line))
              }
            }.mapValues(_.toList)
          }
          val SumName = s"${name}_sum"
          val CountName = s"${name}_count"
          val foo =
          histogramGroups.map { case (labels, lines) ⇒
            val (bucketLines, otherLines) = lines.span(_.name == name)
            val buckets = bucketLines.map { line ⇒
              MetricValue.Bucket(parseValue(line.labels("le")), line.value.toLong)
            }
            val (sum,count) = otherLines match {
              case MetricLine(CountName, _, countValue, _) :: MetricLine(SumName, _, sumValue, _) :: Nil ⇒
                sumValue → countValue.toLong
              case _ ⇒ throw new IllegalArgumentException("invalid lines at end of histogram metric")
            }
            val value = MetricValue.Histogram(buckets, count, sum)
            Metric(value, lines.head.timestamp, labels)
          }.toSeq
          val orig = metricLines.map(_.labels - "le").distinct
          val calc = foo.map(_.labels)
          if (orig != calc) {
            if (orig.size != calc.size) {
              println(s"orig.size(${orig.size}}) != calc.size(${calc.size}})")
            } else {
              orig.zip(calc).zipWithIndex.foreach { p ⇒
                println(p)
              }
            }
          }
          foo
      }
      MetricFamily(name, prometheusType, help, metrics)
    }

    /** Parses an entire snapshot, a sequence of metric familiies. */
    def snapshot: Parser[Seq[MetricFamily]] = rep(metricFamily)
  }
}
