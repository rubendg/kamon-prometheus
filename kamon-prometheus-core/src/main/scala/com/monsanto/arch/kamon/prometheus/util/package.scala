package com.monsanto.arch.kamon.prometheus

/** kamon-prometheus’ incarnation of a generic ‘util’ package. */
package object util {
  /** Predicate that indicates that the items a sequence are strictly increasing. */
  def strictlyIncreasing[T](items: Seq[T])(implicit toOrdered: T => Ordered[T]): Boolean = {
    items.size match {
      case 0 | 1 ⇒ true
      case _ ⇒ items.zip(items.tail).forall(pair ⇒ pair._1 < pair._2)
    }
  }
}
