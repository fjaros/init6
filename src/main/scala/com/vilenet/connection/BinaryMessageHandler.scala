package com.vilenet.connection

import java.net.InetSocketAddress
import java.util.concurrent.TimeUnit

import akka.actor.{ActorRef, FSM, Props}
import akka.io.Tcp.{Write, Received}
import akka.pattern.ask
import akka.util.{Timeout, ByteString}
import com.vilenet.ViLeNetActor
import com.vilenet.channels._
import com.vilenet.coders.binary.hash.{DoubleHash, BrokenSHA1}
import com.vilenet.coders.binary.packets._
import com.vilenet.users.{BinaryProtocol, Add}

import scala.concurrent.Await

sealed trait PacketReceiverState
case object ExpectingHeader extends PacketReceiverState
case object ExpectingPacketId extends PacketReceiverState
case object ExpectingLength extends PacketReceiverState
case object ExpectingData extends PacketReceiverState

sealed trait ReceiverData {
  val data = ByteString.empty
}
case object EmptyMessageReceiverData extends ReceiverData
case class HeaderReceivedData(override val data: ByteString) extends ReceiverData
case class PacketIdReceivedData(packetId: Byte, override val data: ByteString) extends ReceiverData
case class LengthReceivedData(packetId: Byte, packetLength: Int, override val data: ByteString) extends ReceiverData

sealed trait BinaryState
case object StartLoginState extends BinaryState
case object ExpectingSidStartVersioning extends BinaryState
case object ExpectingSidReportVersion extends BinaryState
case object ExpectingSidLogonChallenge extends BinaryState
case object ExpectingSidAuthInfo extends BinaryState
case object ExpectingSidAuthCheck extends BinaryState
case object ExpectingSidLogonResponse extends BinaryState
case object ExpectingSidEnterChat extends BinaryState
case object ExpectingSidJoinChannel extends BinaryState


sealed trait BinaryData
case object EmptyBinaryData extends BinaryData
case class WithBinaryData(packetId: Byte, packetLength: Int, data: Array[Byte]) extends BinaryData
case class WithActor(actor: ActorRef) extends BinaryData

trait DeBuffer {
  implicit def toByte(data: Array[Byte]): Byte = data.head
  implicit def toWord(data: Array[Byte]): Short = (data(1) << 8 & 0xff00 | data(0) & 0xff).toShort
  implicit def toDword(data: Array[Byte]): Int = data(3) << 24 & 0xff000000 | data(2) << 16 & 0xff0000 | data(1) << 8 & 0xff00 | data(0) & 0xff
}

/**
 * Created by filip on 10/25/15.
 */
object BinaryMessageReceiver {
  def apply(clientAddress: InetSocketAddress, connection: ActorRef) = Props(new BinaryMessageReceiver(clientAddress, connection))
}

class BinaryMessageReceiver(clientAddress: InetSocketAddress, connection: ActorRef) extends ViLeNetActor with FSM[PacketReceiverState, ReceiverData] {

  val HEADER_BYTE = 0xFF.toByte
  val HEADER_SIZE = 4

  val handler = context.actorOf(BinaryMessageHandler(clientAddress, connection))

  startWith(ExpectingHeader, EmptyMessageReceiverData)

  when(ExpectingHeader) {
    case Event(Received(data), _) =>
      if (data.head == HEADER_BYTE) {
        goto (ExpectingPacketId) using HeaderReceivedData(data.tail)
      } else {
        stop()
      }
  }

  when(ExpectingPacketId) {
    case Event(Received(data), _) =>
      goto (ExpectingLength) using PacketIdReceivedData(data.head, data.tail)
  }

  when(ExpectingLength) {
    case Event(Received(data), PacketIdReceivedData(packetId, _)) =>
      goto (ExpectingData) using LengthReceivedData(packetId, (data(1) << 8 & 0xff00 | data(0) & 0xff) - HEADER_SIZE, data.drop(2))
  }

  when(ExpectingData) {
    case Event(Received(data), LengthReceivedData(packetId, length, buffer)) =>
      //log.error(s"packet $packetId with length $length ${data.length}")
      if (data.length >= length) {
        handler ! WithBinaryData(packetId, length, data.take(length).toArray)
        goto (ExpectingHeader) using HeaderReceivedData(data.drop(length))
      } else {
        stop()
        //stay()
      }
  }

  onTransition {
    case x -> y =>
      nextStateData match {
        case receiverData: ReceiverData =>
          if (receiverData.data.nonEmpty) {
            self ! Received(receiverData.data)
          }
      }
  }
}

object BinaryMessageHandler {
  def apply(clientAddress: InetSocketAddress, connection: ActorRef) = Props(new BinaryMessageHandler(clientAddress, connection))
}

class BinaryMessageHandler(clientAddress: InetSocketAddress, connection: ActorRef) extends ViLeNetActor with FSM[BinaryState, BinaryData] with DeBuffer {

  implicit val timeout = Timeout(1, TimeUnit.MINUTES)

  startWith(StartLoginState, EmptyBinaryData)
  context.watch(connection)

  val pingCookie: Int = 0xBADCABF
  var pingTime: Long = 0
  var ping: Int = -1

  var clientToken: Int = _
  var username: String = _
  var productId: String = _

  when(StartLoginState) {
    case Event(WithBinaryData(packetId, length, data), _) =>
      if (packetId == 0x05) {
        connection ! Write(SidLogonChallenge())
        connection ! Write(SidPing(pingCookie))
        pingTime = System.currentTimeMillis()
        connection ! Write(SidStartVersioning())
        goto(ExpectingSidStartVersioning)
      } else if (packetId == 0x1E) {
        connection ! Write(SidLogonChallengeEx())
        connection ! Write(SidPing(pingCookie))
        pingTime = System.currentTimeMillis()
        connection ! Write(SidStartVersioning())
        goto(ExpectingSidStartVersioning)
      } else if (packetId == 0x50) {
        productId = new String(data.slice(8, 12))
        connection ! Write(SidPing(pingCookie))
        pingTime = System.currentTimeMillis()
        connection ! Write(SidAuthInfo())
        goto(ExpectingSidAuthCheck)
      } else {
        stop()
      }
  }

  when(ExpectingSidStartVersioning) {
    case Event(WithBinaryData(packetId, length, data), _) =>
      if (packetId == 0x06) {
        productId = new String(data.slice(4, 8))
        goto(ExpectingSidReportVersion)
      } else {
        stay()
      }
  }

  when(ExpectingSidReportVersion) {
    case Event(WithBinaryData(packetId, length, data), _) =>
      if (packetId == 0x07) {
        connection ! Write(SidReportVersion())
        goto(ExpectingSidLogonResponse)
      } else {
        stay()
      }
  }

  when(ExpectingSidLogonResponse) {
    case Event(WithBinaryData(packetId, length, data), _) =>
      if (packetId == 0x29 || packetId == 0x3A) {
        username = new String(data.drop(7*4).takeWhile(_ != 0))
        val u = User(username, 0, client = productId)
        Await.result(usersActor ? Add(connection, u, BinaryProtocol), timeout.duration) match {
          case reply: ActorRef =>
            connection ! Write(SidLogonResponse2())
            goto (ExpectingSidEnterChat) using WithActor(reply)
          case _ => stop()
        }
      } else {
        stay()
      }
  }

  when(ExpectingSidAuthInfo) {
    case Event(WithBinaryData(packetId, length, data), _) =>
      if (packetId == 0x50) {
        productId = new String(data.slice(8, 12))
        connection ! Write(SidAuthInfo())
        goto(ExpectingSidAuthCheck)
      } else {
        stay()
      }
  }


  when(ExpectingSidAuthCheck) {
    case Event(WithBinaryData(packetId, length, data), _) =>
      if (packetId == 0x51) {
        clientToken = toDword(data)
        connection ! Write(SidAuthCheck())
        goto(ExpectingSidLogonResponse)
      } else {
        stay()
      }
  }

  when(ExpectingSidEnterChat) {
    case Event(WithBinaryData(packetId, length, data), _) =>
      if (packetId == 0x0A) {
        connection ! Write(SidEnterChat(username, productId))
        goto(ExpectingSidJoinChannel) using stateData
      } else {
        stay()
      }
  }

  when(ExpectingSidJoinChannel) {
    case Event(WithBinaryData(packetId, length, data), WithActor(actor)) =>
      if (packetId == 0x0C) {
        actor ! Received(ByteString(s"/j ${new String(data.drop(4).takeWhile(_ != 0))}"))
      } else if (packetId == 0x0E) {
        actor ! Received(ByteString(data.slice(0, data.length - 1)))
      } else {
      }
      stay()
  }

//  whenUnhandled {
//    case Event(WithBinaryData(packetId, length, data), WithActor(actor)) =>
//      if (packetId == 0x25) {
//        ping = Math.max(0, (System.currentTimeMillis() - pingTime).toInt)
//      }
//      stay()
//  }

  onTermination {
    case _ =>
      log.error("Connection stopped 4")
      context.stop(self)
  }
}
