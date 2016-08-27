package com.monsanto.arch.kamon.prometheus.converter

import java.util.regex.Pattern

/** Container for various utility functions necessary for munging names from Kamon so that they are usable by
  * Prometheus.
  *
  * @author Daniel Solano Gómez
  */
private[converter] object Mungers {
  /** Creates a munging function from a regular expression pattern. */
  private def makeMunger(pattern: Pattern)(str: String) = pattern.matcher(str).replaceAll("_")

  /** A munger for all characters which may not appear in a Prometheus metric name. */
  private val nonMetricNameCharMunger = makeMunger("[^a-zA-Z0-9:_]".r.pattern)(_)

  /** A munger for all characters which may not appear in a Prometheus label name. */
  private val nonLabelNameCharMunger = makeMunger("[^a-zA-Z0-9_]".r.pattern)(_)

  /** If the input is empty, returns `"_"`; otherwise returns the input. */
  private def nonEmptyOrUnderscore(str: String): String = if(str.isEmpty) "_" else str

  /** If the given string begins with a digit, return a new string where it is replaced with an underscroe.
    * Otherwise, returns the input.
    */
  private def mungeLeadingDigit(str: String): String = if(str.charAt(0).isDigit) s"_${str.substring(1)}" else str

  /** Replaces multiple leading underscores into a single one. */
  private val collapseLeadingUnderscores = makeMunger("^__+".r.pattern)(_)

  /** Munges a name until it is usable as a Prometheus metric name.  This is accomplished by converting all
    * disallowed characters into underscores.  Additionally, any name that begins with a digit will have that digit
    * replaced with an underscore.  In the unlikely case that an empty string is passed as a name, it is converted
    * to a string containing just an underscore.
    */
  def asMetricName(name: String): String =
    (nonEmptyOrUnderscore _ andThen nonMetricNameCharMunger andThen mungeLeadingDigit)(name)

  /** Munges a name until it is usable as a Prometheus label name.  This is accomplished by converting all
    * disallowed characters into underscores.  Additionally, any name that begins with a digit will have that digit
    * replaced with an underscore.  In the unlikely case that an empty string is passed as a name, it is converted
    * to a string containing just an underscore.  Lastly, since all label names beginning with two underscores are
    * reserved by Prometheus, any prefixes consisting of two or more underscores will be replaced with a single
    * underscore.
    */
  def asLabelName(name: String): String =
    (nonEmptyOrUnderscore _
      andThen nonLabelNameCharMunger
      andThen mungeLeadingDigit
      andThen collapseLeadingUnderscores)(name)
}
