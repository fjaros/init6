package com.init6.connection

import akka.actor.{ActorRef, FSM, Props}
import akka.io.Tcp._
import akka.util.ByteString
import com.init6.Init6Actor
import com.init6.connection.binary.BinaryMessageHandler
import com.init6.connection.chat1.Chat1Handler
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
case class ConnectionProtocolData(packetReceiver: PacketReceiver[_], messageHandler: ActorRef, data: ByteString) extends ProtocolData

class ProtocolHandler(rawConnectionInfo: ConnectionInfo) extends Init6Actor with FSM[ProtocolState, ProtocolData] {

  val BINARY: Byte = 0x01
  val TELNET: Byte = 0x03
  val TELNET_2: Byte = 0x04
  val INIT6_CHAT: Byte = 'C'.toByte
  val INIT6_CHAT_1: Byte = '1'.toByte

  var hasAccepted = false

  override def preStart() = {
    super.preStart()

    startWith(Uninitialized, EmptyProtocolData)
    rawConnectionInfo.actor ! ResumeReading
  }

  when(Uninitialized) {
    case Event(Received(data), _) =>
      val packetReceivedTime = getAcceptingUptime.toNanos
      var isC1 = false
      val protocolData =
        if (data.head == BINARY) {
          Some(ConnectionProtocolData(new BinaryReceiver, context.actorOf(BinaryMessageHandler(
            rawConnectionInfo.copy(actor = self, firstPacketReceivedTime = packetReceivedTime, protocol = BinaryProtocol))), data.drop(1)))
        } else if (data(0) == TELNET) {
          Some(ConnectionProtocolData(new ChatReceiver, context.actorOf(TelnetMessageHandler(
            rawConnectionInfo.copy(actor = self, firstPacketReceivedTime = packetReceivedTime, protocol = TelnetProtocol))), data.drop({
              if (data(1) == TELNET_2) {
                2
              } else {
                1
              }
          })))
        } else if (data(0) == INIT6_CHAT && data(1) == INIT6_CHAT_1) {
          isC1 = true
          Some(ConnectionProtocolData(new ChatReceiver, context.actorOf(Chat1Handler(
            rawConnectionInfo.copy(actor = self, firstPacketReceivedTime = packetReceivedTime, protocol = Chat1Protocol))), data.drop(2)))
        } else {
          None
        }

      protocolData.fold(stop())(protocolData => {
        if (isC1) {
          protocolData.messageHandler ! protocolData.packetReceiver.parsePacket(protocolData.data)
        } else {
          protocolData.packetReceiver.parsePacket(protocolData.data).foreach(protocolData.messageHandler ! _)
        }
        rawConnectionInfo.actor ! ResumeReading
        goto (Initialized) using protocolData
      })
    case _ => stop()
  }

  var buffer = Vector[ByteString]()

  when(Initialized) {
    case Event(Received(data), protocolData: ConnectionProtocolData) =>
      protocolData.packetReceiver.parsePacket(data).foreach(protocolData.messageHandler ! _)
      rawConnectionInfo.actor ! ResumeReading
      stay()
    case Event(_: ConnectionClosed, _) =>
      stop()
    case Event(WriteOut(data), protocolData: ConnectionProtocolData) =>
      rawConnectionInfo.actor ! Write(data, Ack)
      sender() ! WrittenOut
      goto (InitializedBuffering)
    case Event(resumeAccepting @ ResumeAccepting(batchSize), protocolData: ConnectionProtocolData) =>
      context.parent ! resumeAccepting
      hasAccepted = true
      stay()
    case Event(x, protocolData: ConnectionProtocolData) =>
      protocolData.messageHandler ! x
      stop()
    case x =>
      log.error("{} ProtocolHandler InitializedBuffering unhandled: {}", rawConnectionInfo.ipAddress.getAddress.getHostAddress, x)
      stay()
  }

  when(InitializedBuffering) {
    case Event(Received(data), protocolData: ConnectionProtocolData) =>
      protocolData.packetReceiver.parsePacket(data).foreach(protocolData.messageHandler ! _)
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
    case Event(resumeAccepting @ ResumeAccepting(batchSize), protocolData: ConnectionProtocolData) =>
      context.parent ! resumeAccepting
      hasAccepted = true
      stay()
    case Event(x, protocolData: ConnectionProtocolData) =>
      protocolData.messageHandler ! x
      stop()
    case x =>
      log.error("{} ProtocolHandler InitializedBuffering unhandled: {}", rawConnectionInfo.ipAddress.getAddress.getHostAddress, x)
      stay()
  }

  onTermination {
    case terminated =>
      if (!hasAccepted) {
        context.parent ! ResumeAccepting(1)
      }
      ipLimiterActor ! Disconnected(rawConnectionInfo.actor)
  }
}
