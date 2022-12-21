package com.init6.connection.websocket

import java.net.InetSocketAddress

import akka.actor.{ActorRef, FSM, Props}
import akka.util.ByteString
import com.init6.Init6Actor
import com.init6.connection.chat1.Chat1Handler
import com.init6.connection.{ChatReceiver, ConnectionInfo, WriteOut, WrittenOut}

/**
  * Created by filip on 3/12/17.
  */
object WebSocketProtocolHandler {

  def apply(webSocketRawConnectionInfo: WebSocketRawConnectionInfo) =
    Props(classOf[WebSocketProtocolHandler], webSocketRawConnectionInfo)
}

sealed trait WebSocketState
case object WaitingForSourceActor extends WebSocketState
case object Connected extends WebSocketState
case object ExpectingData extends WebSocketState

case class WebSocketRawConnectionInfo(
  ipAddress: InetSocketAddress,
  connectedTime: Long,
  place: Int = -1
)
case class WebSocketConnectedData(
  webSocketActor: ActorRef,
  packetReceiver: ChatReceiver,
  handlerActor: ActorRef
)

class WebSocketProtocolHandler(webSocketRawConnectionInfo: WebSocketRawConnectionInfo)
  extends Init6Actor with FSM[WebSocketState, Any] {

  val INIT6_CHAT: Byte = 'C'.toByte
  val INIT6_CHAT_1: Byte = '1'.toByte

  override def preStart() = {
    super.preStart()

    startWith(WaitingForSourceActor, webSocketRawConnectionInfo)
  }

  when(WaitingForSourceActor) {
    case Event(ChangeConnectionInfoActor(actor), webSocketRawConnectionInfo: WebSocketRawConnectionInfo) =>
      val connectionInfo = ConnectionInfo(
        ipAddress = webSocketRawConnectionInfo.ipAddress,
        actor = actor,
        connectedTime = webSocketRawConnectionInfo.connectedTime,
        place = webSocketRawConnectionInfo.place
      )
      goto(Connected) using connectionInfo
    case x =>
      stop()
  }

  when(Connected) {
    case Event(data: ByteString, connectionInfo: ConnectionInfo) =>
      if (data.length >= 2) {
        if (data(0) == INIT6_CHAT && data(1) == INIT6_CHAT_1) {
          val packetReceiver = new ChatReceiver
          val handlerActor = context.actorOf(Chat1Handler(connectionInfo.copy(actor = self)))
          if (data.length > 2) {
            packetReceiver.parsePacket(data.drop(2)).foreach(handlerActor ! _)
          }
          goto(ExpectingData) using WebSocketConnectedData(connectionInfo.actor, packetReceiver, handlerActor)
        } else {
          stop()
        }
      } else {
        stop()
      }
    case _ =>
      stop()
  }

  when(ExpectingData) {
    case Event(data: ByteString, WebSocketConnectedData(_, packetReceiver, handlerActor)) =>
      packetReceiver.parsePacket(data).foreach(handlerActor ! _)
      stay()
    case Event(WriteOut(data), WebSocketConnectedData(webSocketActor, _, handlerActor)) =>
      webSocketActor ! data
      sender() ! WrittenOut
      stay()
  }
}
