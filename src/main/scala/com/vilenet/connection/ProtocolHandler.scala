package com.vilenet.connection

import java.net.InetSocketAddress

import akka.actor.{FSM, Props, ActorRef}
import akka.io.Tcp._
import akka.util.ByteString
import com.vilenet.ViLeNetActor

object ProtocolHandler {
  def apply(clientAddress: InetSocketAddress, client: ActorRef) = Props(new ProtocolHandler(clientAddress, client))
}

case object Ack extends Event
case class WriteOut(data: ByteString)

sealed trait ProtocolState
case object Uninitialized extends ProtocolState
case object Initialized extends ProtocolState
case object InitializedBuffering extends ProtocolState

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
          goto (Initialized) using ConnectionProtocolData(context.actorOf(BinaryMessageReceiver(clientAddress, self)), data.tail)
        case TELNET =>
          val dataTail = data.tail
          dataTail.head match {
            case TELNET_2 =>
              goto (Initialized) using ConnectionProtocolData(context.actorOf(TelnetMessageReceiver(clientAddress, self)), dataTail.tail)
            case _ => stop()
          }
        case _ => stop()
      }
    case _ => stop()
  }

  var buffer = Vector[ByteString]()

  when(Initialized) {
    case Event(Received(data), protocolData: ConnectionProtocolData) =>
      protocolData.messageHandler ! Received(data)
      client ! ResumeReading
      stay()
    case Event(WriteOut(data), protocolData: ConnectionProtocolData) =>
      ////log.error(s"### WriteOut1: $client ${data.utf8String}")
      client ! Write(data, Ack)
      goto (InitializedBuffering)
    case Event(x, protocolData: ConnectionProtocolData) =>
      ////log.error(s"### RECEIVE3 connection $client message $x")
      protocolData.messageHandler ! x
      stop()
    case x =>
      //log.error(s"#x ? $x")
      stay()
  }

  when(InitializedBuffering) {
    case Event(Received(data), protocolData: ConnectionProtocolData) =>
      protocolData.messageHandler ! Received(data)
      client ! ResumeReading
      stay()
    case Event(WriteOut(data), _) =>
     // //log.error(s"### WriteOut2: $client ${data.utf8String}")
      buffer :+= data
      stay()
    case Event(Ack, _) =>
      ////log.error(s"### Ack: $client ${buffer.size}")
      buffer
        .headOption
        .fold(goto(Initialized))(data => {
          client ! Write(data, Ack)
          buffer = buffer.drop(1)
          stay()
        })
    case Event(x, protocolData: ConnectionProtocolData) =>
      ////log.error(s"### RECEIVE3 connection $client message $x")
      protocolData.messageHandler ! x
      stop()
    case x =>
      //log.error(s"#x ? $x")
      stay()
  }

  onTransition {
    case Uninitialized -> Initialized =>
      nextStateData match {
        case ConnectionProtocolData(actor, data) =>
          if (data.nonEmpty) {
            self ! Received(data)
          } else {
            client ! ResumeReading
          }
        case _ =>
      }
    case _ => stop()
  }

  onTermination {
    case _ => ipLimiterActor ! Disconnected(clientAddress.getAddress.getAddress)
  }
}
