package com.monsanto.arch.kamon.prometheus.demo

import com.monsanto.arch.kamon.prometheus.spray.SprayEndpoint
import com.monsanto.arch.kamon.spray.routing.TracingHttpServiceActor
import kamon.Kamon

import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

/** A simple spray service that contains a simple endpoint and a metrics endpoint that Prometheus can consume.
  *
  * @author Daniel Solano Gómez
  */
class DemoServiceActor extends TracingHttpServiceActor {
  import DemoServiceActor._

  override implicit val actorRefFactory = context
  override def receive = runRoute(metricsRoute ~ demoRoute ~ metricRoutes ~ timesOutRoute ~ errorRoute)
  var minMaxCount = 0L

  /** A simple endpoint for `/` which also gives all traces that end up here the name ‘demo-endpoint’. */
  val demoRoute =
    path("") {
      get {
        complete {
          "Welcome to the demo for Kamon/Prometheus."
        }
      }
    }

  /** A route used to increment a counter. */
  val metricRoutes =
    post {
      path("counter" / IntNumber ) { counterNumber ⇒
        validate(counterNumber >= 0 && counterNumber < CounterNames.size, s"Invalid counter number: $counterNumber") {
          complete {
            val name = CounterNames(counterNumber)
            Kamon.metrics.counter("basic-counter", Map("name" → name)).increment()
            s"Incremented counter #$name"
          }
        }
      } ~
      path("min-max-counter") {
        val mmc = Kamon.metrics.minMaxCounter("basic-min-max-counter", refreshInterval = 5.seconds)
        complete {
          val change = ((math.random * 50.0).toLong - 25L).max(-minMaxCount)
          minMaxCount += change
          assert(minMaxCount >= 0)

          mmc.increment(change)

          minMaxCount.toString
        }
      } ~
      path("histogram") {
        val h = Kamon.metrics.histogram("basic-histogram")
        complete {
          val value = System.currentTimeMillis() % 1000L
          h.record(value)

          value.toString
        }
      }
    }

  /** A route that times out after a while. */
  val timesOutRoute =
    get {
      path("timeout") {
        dynamic {
          implicit val ec = context.dispatcher
          val result: Future[String] = Future {
            Thread.sleep(1100.milliseconds.toMillis)
            "This should time out."
          }
          onSuccess(result) { text ⇒
            complete(text)
          }
        }
      }
    }

  /** A route that throws an exception. */
  val errorRoute =
    get {
      path("error") {
        complete((1 / 0).toString)
      }
    }

  /** This directive creates the metrics endpoint at `/metrics` and names all traces which end up here ‘metrics’. */
  val metricsRoute =
    path("metrics") {
      SprayEndpoint(context.system).route
    }
}

object DemoServiceActor {
  /** The valid counter names. */
  val CounterNames = IndexedSeq("α", "β", "γ", "δ", "ε")
}
