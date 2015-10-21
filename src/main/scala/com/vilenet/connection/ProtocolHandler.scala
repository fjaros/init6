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
case object Binary extends ProtocolState
case object Telnet extends ProtocolState

sealed trait ProtocolData
case object EmptyProtocolData extends ProtocolData
case class TelnetProtocolData(messageHandler: ActorRef, data: ByteString) extends ProtocolData

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
          //goto (Binary) using data.tail
          stop()
        case TELNET =>
          val dataTail = data.tail
          dataTail.head match {
            case TELNET_2 =>
              goto (Telnet) using TelnetProtocolData(context.actorOf(TelnetMessageReceiver(clientAddress, client)), dataTail.tail)
            case _ => stop()
          }
        case _ => stop()
      }
    case _ => stop()
  }

  when(Telnet) {
    case Event(Received(data), protocolData: TelnetProtocolData) =>
      protocolData.messageHandler ! Received(data)
      client ! ResumeReading
      stay()
    case Event(x, protocolData: TelnetProtocolData) =>
      protocolData.messageHandler ! x
      stop()
  }

  onTransition {
    case Uninitialized -> Telnet =>
      nextStateData match {
        case TelnetProtocolData(actor, data) =>
          self ! Received(data)
        case _ =>
      }
    case Uninitialized -> Binary =>

    case _ => stop()
  }
}
