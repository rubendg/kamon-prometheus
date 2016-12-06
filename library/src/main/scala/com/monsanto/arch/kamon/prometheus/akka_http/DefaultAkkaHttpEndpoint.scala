package com.monsanto.arch.kamon.prometheus.akka_http

import java.util.concurrent.atomic.AtomicReference

import akka.actor.ActorDSL._
import akka.actor.{ActorRefFactory, ExtendedActorSystem}
import akka.event.Logging
import akka.http.scaladsl.marshalling.Marshaller._
import akka.http.scaladsl.marshalling._
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Route
import com.monsanto.arch.kamon.prometheus.akka_http.DefaultAkkaHttpEndpoint.MediaTypes._
import com.monsanto.arch.kamon.prometheus.metric.{MetricFamily, ProtoBufFormat, TextFormat}
import com.monsanto.arch.kamon.prometheus.{Prometheus, PrometheusExtension}

import scala.util.{Failure, Success}

/** Implementation of the [[AkkaHttpEndpoint]] interface.
  *
  * @author Daniel Solano Gómez
  */
private[akka_http] class DefaultAkkaHttpEndpoint(system: ExtendedActorSystem) extends AkkaHttpEndpoint {
  private implicit val arf: ActorRefFactory = system

  /** Mutable cell with the latest snapshot. */
  private[akka_http] val snapshot = new AtomicReference[Seq[MetricFamily]]

  private val snapshotTextEntityMarshaller: ToEntityMarshaller[Seq[MetricFamily]] =
    StringMarshaller.wrap(`text/plain;version=0.0.4`)(TextFormat.format)

  private val snapshotProtoBufEntityMarshaller: ToEntityMarshaller[Seq[MetricFamily]] =
    ByteArrayMarshaller.wrap(`application/vnd.google.protobuf`)(ProtoBufFormat.format)

  private implicit val snapshotEntityMarshaller: ToEntityMarshaller[Seq[MetricFamily]] =
    Marshaller.oneOf(snapshotTextEntityMarshaller, snapshotProtoBufEntityMarshaller)

  private implicit val foo: ToResponseMarshaller[Option[Seq[MetricFamily]]] =
    Marshaller { implicit ec ⇒
      {
        case Some(s) ⇒
          implicitly[ToResponseMarshaller[Seq[MetricFamily]]].apply(s)
        case None ⇒
          implicitly[ToResponseMarshaller[HttpResponse]].apply(HttpResponse(StatusCodes.NoContent))
      }
    }

  override val route: Route = {
    import akka.http.scaladsl.server.Directives._

    get {
      encodeResponse {
        complete(Option(snapshot.get))
      }
    }
  }

  private val updater = actor(new Act {
    become {
      case PrometheusExtension.Snapshot(s) ⇒ snapshot.set(s)
    }
  })

  Prometheus.kamonInstance.onComplete { result ⇒
    val log = Logging(system, classOf[AkkaHttpEndpoint])
    result match {
      case Success(ext) ⇒
        log.debug("AkkaHttpEndpoint is subscribing to Kamon Prometheus updates")
        ext.ref.tell(PrometheusExtension.Subscribe, updater)
      case Failure(t) ⇒
        log.error(t, "Kamon Prometheus module failed to load, AkkaHttpEndpoint will never serve data")
    }
  }(system.dispatcher)
}

private[akka_http] object DefaultAkkaHttpEndpoint {
  object MediaTypes {
    val `application/vnd.google.protobuf` =
      MediaType.customBinary("application", "vnd.google.protobuf", MediaType.Compressible,
        params = Map("proto" → "io.prometheus.client.MetricFamily", "encoding" → "delimited"))

    val `text/plain;version=0.0.4` =
      MediaType.customWithFixedCharset("text", "plain",
        HttpCharsets.`UTF-8`, params = Map("version" → "0.0.4"))
  }
}
