package com.init6.connection

import akka.actor.{ActorRef, FSM, Props}
import akka.io.Tcp._
import akka.util.ByteString
import com.init6.Init6Actor
import com.init6.connection.chat1.Chat1Receiver
import com.init6.users.{BinaryProtocol, Chat1Protocol, TelnetProtocol}

object ProtocolHandler {
  def apply(rawConnectionInfo: ConnectionInfo) = Props(classOf[ProtocolHandler], rawConnectionInfo)
}

case object Ack extends Event
case class WriteOut(data: ByteString)
case object WrittenOut

sealed trait ProtocolState
case object Uninitialized extends ProtocolState
case object Uninitialized2Telnet extends ProtocolState
case object Uninitialized2Chat1 extends ProtocolState
case object Initialized extends ProtocolState
case object InitializedBuffering extends ProtocolState

sealed trait ProtocolData
case object EmptyProtocolData extends ProtocolData
case class ConnectingProtocolData(bufferedData: ByteString) extends ProtocolData
case class ConnectionProtocolData(messageHandler: ActorRef, data: ByteString) extends ProtocolData

class ProtocolHandler(rawConnectionInfo: ConnectionInfo) extends Init6Actor with FSM[ProtocolState, ProtocolData] {

  val BINARY: Byte = 0x01
  val TELNET: Byte = 0x03
  val TELNET_2: Byte = 0x04
  val INIT6_CHAT: Byte = 'C'.toByte
  val INIT6_CHAT_1: Byte = '1'.toByte

  override def preStart() = {
    super.preStart()

    startWith(Uninitialized, EmptyProtocolData)
    rawConnectionInfo.actor ! ResumeReading
  }

  when(Uninitialized) {
    case Event(Received(data), _) =>
      data.head match {
        case BINARY =>
          goto (Initialized) using ConnectionProtocolData(context.actorOf(BinaryMessageReceiver(
            rawConnectionInfo.copy(actor = self, protocol = BinaryProtocol)))
            , data.tail)
        case TELNET =>
          goto (Uninitialized2Telnet) using ConnectingProtocolData(data.tail)
        case INIT6_CHAT =>
          goto (Uninitialized2Chat1) using ConnectingProtocolData(data.tail)
        case _ => stop()
      }
    case _ => stop()
  }

  when (Uninitialized2Telnet) {
    case Event(Received(data), protocolData: ConnectingProtocolData) =>
      data.head match {
        case TELNET_2 =>
          goto (Initialized) using ConnectionProtocolData(context.actorOf(TelnetMessageReceiver(
            rawConnectionInfo.copy(actor = self, protocol = TelnetProtocol)))
            , data.tail)
        case _ => stop()
      }
    case _ => stop()
  }

  when (Uninitialized2Chat1) {
    case Event(Received(data), protocolData: ConnectingProtocolData) =>
      data.head match {
        case INIT6_CHAT_1 =>
          goto (Initialized) using ConnectionProtocolData(
            context.actorOf(Chat1Receiver(
              rawConnectionInfo.copy(actor = self, protocol = Chat1Protocol)))
            , data.tail)
        case _ => stop()
      }
    case _ => stop()
  }

  var buffer = Vector[ByteString]()

  when(Initialized) {
    case Event(Received(data), protocolData: ConnectionProtocolData) =>
      protocolData.messageHandler ! Received(data)
      rawConnectionInfo.actor ! ResumeReading
      stay()
    case Event(_: ConnectionClosed, _) =>
      stop()
    case Event(WriteOut(data), protocolData: ConnectionProtocolData) =>
      ////log.error(s"### WriteOut1: $client ${data.utf8String}")
      rawConnectionInfo.actor ! Write(data, Ack)
      sender() ! WrittenOut
      goto (InitializedBuffering)
    case Event(x, protocolData: ConnectionProtocolData) =>
      ////log.error(s"### RECEIVE3 connection $client message $x")
      protocolData.messageHandler ! x
      stop()
    case x =>
      log.error("{} ProtocolHandler InitializedBuffering unhandled: {}", rawConnectionInfo.ipAddress.getAddress.getHostAddress, x)
      stay()
  }

  when(InitializedBuffering) {
    case Event(Received(data), protocolData: ConnectionProtocolData) =>
      protocolData.messageHandler ! Received(data)
      rawConnectionInfo.actor ! ResumeReading
      stay()
    case Event(_: ConnectionClosed, _) =>
      stop()
    case Event(WriteOut(data), _) =>
      buffer :+= data
      sender() ! WrittenOut
      stay()
    case Event(Ack, _) =>
      buffer
        .headOption
        .fold(goto(Initialized))(data => {
          rawConnectionInfo.actor ! Write(data, Ack)
          buffer = buffer.drop(1)
          stay()
        })
    case Event(x, protocolData: ConnectionProtocolData) =>
      ////log.error(s"### RECEIVE3 connection $client message $x")
      protocolData.messageHandler ! x
      stop()
    case x =>
      log.error("{} ProtocolHandler InitializedBuffering unhandled: {}", rawConnectionInfo.ipAddress.getAddress.getHostAddress, x)
      stay()
  }

  onTransition {
    case Uninitialized -> Initialized |
         Uninitialized2Telnet -> Initialized |
         Uninitialized2Chat1 -> Initialized =>
      nextStateData match {
        case ConnectionProtocolData(actor, data) =>
          if (data.nonEmpty) {
            self ! Received(data)
          } else {
            rawConnectionInfo.actor ! ResumeReading
          }
        case x =>
          log.error("{} ProtocolHandler onTransition unhandled state data: {}", rawConnectionInfo.ipAddress.getAddress.getHostAddress, x)
      }
    case Uninitialized -> Uninitialized2Telnet => transitionToExpectByte2()
    case Uninitialized -> Uninitialized2Chat1 => transitionToExpectByte2()
    case Initialized -> InitializedBuffering =>
    case InitializedBuffering -> Initialized =>
    case x =>
      log.error("{} ProtocolHandler onTransition unhandled transition: {}", rawConnectionInfo.ipAddress.getAddress.getHostAddress, x)
  }

  def transitionToExpectByte2() = {
    nextStateData match {
      case ConnectingProtocolData(data) =>
        if (data.nonEmpty) {
          self ! Received(data)
        } else {
          rawConnectionInfo.actor ! ResumeReading
        }
      case x =>
        log.error("{} ProtocolHandler onTransition unhandled state data: {}", rawConnectionInfo.ipAddress.getAddress.getHostAddress, x)
    }
  }

  onTermination {
    case terminated =>
      ipLimiterActor ! Disconnected(rawConnectionInfo.actor)
  }
}
