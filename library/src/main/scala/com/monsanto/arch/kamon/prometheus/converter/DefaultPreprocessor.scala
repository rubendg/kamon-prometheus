package com.monsanto.arch.kamon.prometheus.converter

/** The default preprocessor makes a number of modifications to make querying some Kamon-generated metrics nicer.
  *
  * In particular, it makes the following changes:
  *
  *  1. Adds an `actor_name` label to Akka actor metrics
  *  1. Adds a `dispatcher_name` label to Akka dispatcher metrics
  *  1. By default, both types of Akka dispatchers are grouped into the same
  *     category and are differentiated by a tag.  This is potentially
  *     confusing as they both have `active-threads` instruments with slightly
  *     different semantics.  This preprocessor splits them into two different
  *     metric families by modifying the category name.
  *
  * @author Daniel Solano Gómez
  */
class DefaultPreprocessor extends Preprocessor {
  override def apply(metricSnapshot: MetricSnapshot): MetricSnapshot = {
    metricSnapshot.category match {
      case "akka-actor" ⇒
        metricSnapshot.updateTags(_ + ("actor_name" → metricSnapshot.name))
      case "akka-dispatcher" ⇒
        metricSnapshot.updateTags(_ + ("dispatcher_name" → metricSnapshot.name)).withCategory {
          metricSnapshot.tags("dispatcher_type") match {
            case "fork-join-pool" ⇒ "akka_fork_join_pool_dispatcher"
            case "thread-pool-executor" ⇒ "akka_thread_pool_executor_dispatcher"
          }
        }
      case "akka-router" ⇒
        metricSnapshot.updateTags(_ + ("router_name" → metricSnapshot.name))
      case _ ⇒ metricSnapshot
    }
  }
}
