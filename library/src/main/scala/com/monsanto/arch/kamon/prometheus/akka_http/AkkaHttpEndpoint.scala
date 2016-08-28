package com.monsanto.arch.kamon.prometheus.akka_http

import akka.actor.{ExtendedActorSystem, Extension, ExtensionId, ExtensionIdProvider}
import akka.http.scaladsl.server.Route

/** An Akka extension for creating an Akka HTTP-based endpoint for serving
  * Kamon metrics to Prometheus.  It supports both the text and protocol buffer
  * formats and optional gzip encoding.  All content negotiation is handled
  * through Akka HTTP.
  *
  * ==Usage==
  *
  * As an Akka extension, you will need to instantiate the extension using
  * an actor system.  Once you have an a reference to the extension, you
  * can simply call [[AkkaHttpEndpoint.route route]] to get a Spray route
  * that will handle requests from Prometheus.
  *
  * {{{
  *   import akka.actor.ActorSystem
  *   import akka.http.scaladsl.Http
  *   import akka.http.scaladsl.server.Directives._
  *   import akka.stream.ActorMaterializer
  *   import com.monsanto.arch.kamon.prometheus.akka_http.AkkaHttpEndpoint
  *
  *   object Main extends App {
  *     implicit val system = ActorSystem()
  *     implicit val materializer = ActorMaterializer()
  *
  *     val route = {
  *       path("metrics") {
  *         AkkaHttpEndpoint(system).route
  *       }
  *     }
  *
  *     val bindingFuture = Http().bindAndHandle(route, "localhost", 8888)
  *   }
  * }}}
  *
  * '''NOTE:'''  When you instantiate the extension, it will asynchronously
  * register itself with the Kamon Prometheus extension and start receiving
  * snapshots.  '''However, if Kamon fails to instantiate the Prometheus
  * extension, the Spray endpoint will never serve any data.'''  Errors will
  * be logged.
  *
  * @author Daniel Solano Gómez
  */
trait AkkaHttpEndpoint extends Extension {
  def route: Route
}

/** The Akka extension support for the `AkkaHttpEndpoint`. */
object AkkaHttpEndpoint extends ExtensionId[AkkaHttpEndpoint] with ExtensionIdProvider {
  override def createExtension(system: ExtendedActorSystem): AkkaHttpEndpoint =
    new DefaultAkkaHttpEndpoint(system)

  override def lookup(): ExtensionId[_ <: Extension] = AkkaHttpEndpoint
}
