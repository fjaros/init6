package com.init6.connection.websocket

import java.net.InetSocketAddress
import java.util.concurrent.TimeUnit

import akka.NotUsed
import akka.actor.{ActorRef, PoisonPill, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpMethods.GET
import akka.http.scaladsl.model.ws.{Message, TextMessage, UpgradeToWebSocket}
import akka.http.scaladsl.model.{HttpRequest, HttpResponse, Uri}
import akka.pattern.ask
import akka.stream.scaladsl.{Flow, Sink, Source}
import akka.stream.{ActorMaterializer, OverflowStrategy}
import akka.util.{ByteString, Timeout}
import com.init6.connection.{Allowed, ConnectionInfo, NotAllowed}
import com.init6.{Config, Init6Actor, Init6Component}

import scala.concurrent.Future

/**
  * Created by filip on 3/11/17.
  */
object WebSocketConnectionHandler extends Init6Component {

  def apply() = system.actorOf(Props(classOf[WebSocketConnectionHandler]))
}

case class ChangeConnectionInfoActor(actor: ActorRef)

class WebSocketConnectionHandler extends Init6Actor {

  implicit val materializer = ActorMaterializer()(system)

  override def preStart() = {
    super.preStart()

    Http()
      .bind(interface = Config().Server.host, port = Config().Server.websocketPort)
      .runWith(
        Sink.foreach(conn => {
          conn.handleWithAsyncHandler(requestHandler(conn.remoteAddress))
        })
      )
  }

  override def receive = {
    case _ =>
  }

  def requestHandler(remote: InetSocketAddress): HttpRequest => Future[HttpResponse] = {
    case req @ HttpRequest(GET, Uri.Path("/init6"), _, _, _) =>
      req.header[UpgradeToWebSocket].fold(
        Future.successful(HttpResponse(400, entity = "Not a valid websocket request!"))
      )(upgrade => {
//        import context.dispatcher
        val rawConnectionInfo = WebSocketRawConnectionInfo(remote, getAcceptingUptime.toNanos)
//
//        implicit val timeout = Timeout(1, TimeUnit.SECONDS)
//        (ipLimiterActor ? com.init6.connection.Connected(
//          ConnectionInfo(ipAddress = rawConnectionInfo.ipAddress, actor = self, connectedTime = rawConnectionInfo.connectedTime)
//        ))
//          .map {
//            case Allowed(_) =>
              Future.successful(upgrade.handleMessages(messageHandler(rawConnectionInfo)))
//            case NotAllowed(_) =>
//              HttpResponse(403, entity = "Too many connections!")
//          }
      })
    case r: HttpRequest =>
      r.discardEntityBytes() // important to drain incoming HTTP Entity stream
      Future.successful(HttpResponse(404, entity = "Unknown resource!"))
  }

  def messageHandler(rawConnectionInfo: WebSocketRawConnectionInfo): Flow[Message, Message, NotUsed] = {
    val protocolHandler = context.actorOf(WebSocketProtocolHandler(rawConnectionInfo))

    val incomingMessages: Sink[Message, NotUsed] =
      Flow[Message]
        .map {
          case TextMessage.Strict(text) => ByteString(text)
        }
        .to(Sink.actorRef[ByteString](protocolHandler, PoisonPill))

    val outgoingMessages: Source[Message, NotUsed] =
      Source.actorRef[ByteString](10000, OverflowStrategy.fail)
        .mapMaterializedValue(actor => {
          protocolHandler ! ChangeConnectionInfoActor(actor)
          NotUsed
        })
        .map(data => {
          TextMessage(data.utf8String)
        })

    Flow.fromSinkAndSource(incomingMessages, outgoingMessages)
  }
}
