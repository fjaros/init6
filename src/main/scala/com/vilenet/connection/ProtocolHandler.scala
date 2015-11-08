package com.vilenet.connection

import java.net.InetSocketAddress

import akka.actor.{FSM, Props, ActorRef}
import akka.io.Tcp.{Register, Event, ResumeReading, Received}
import akka.util.ByteString
import com.vilenet.ViLeNetActor

object ProtocolHandler {
  def apply(clientAddress: InetSocketAddress, client: ActorRef) = Props(new ProtocolHandler(clientAddress, client))
}

sealed trait ProtocolState
case object Uninitialized extends ProtocolState
case object Initialized extends ProtocolState

sealed trait ProtocolData
case object EmptyProtocolData extends ProtocolData
case class ConnectionProtocolData(messageHandler: ActorRef, data: ByteString) extends ProtocolData

class ProtocolHandler(clientAddress: InetSocketAddress, client: ActorRef) extends ViLeNetActor with FSM[ProtocolState, ProtocolData] {

  val BINARY: Byte = 0x01
  val TELNET: Byte = 0x03
  val TELNET_2: Byte = 0x04

  startWith(Uninitialized, EmptyProtocolData)

  client ! ResumeReading

  when(Uninitialized) {
    case Event(Received(data), _) =>
      data.head match {
        case BINARY =>
          goto (Initialized) using ConnectionProtocolData(context.actorOf(BinaryMessageReceiver(clientAddress, client)), data.tail)
        case TELNET =>
          val dataTail = data.tail
          dataTail.head match {
            case TELNET_2 =>
              goto (Initialized) using ConnectionProtocolData(context.actorOf(TelnetMessageReceiver(clientAddress, client)), dataTail.tail)
            case _ => stop()
          }
        case _ => stop()
      }
    case _ => stop()
  }

  when(Initialized) {
    case Event(Received(data), protocolData: ConnectionProtocolData) =>
      protocolData.messageHandler ! Received(data)
      client ! ResumeReading
      stay()
    case Event(x, protocolData: ConnectionProtocolData) =>
      protocolData.messageHandler ! x
      stop()
  }

  onTransition {
    case Uninitialized -> Initialized =>
      nextStateData match {
        case ConnectionProtocolData(actor, data) =>
          if (data.nonEmpty) {
            self ! Received(data)
          }
        case _ =>
      }
    case _ => stop()
  }
}
