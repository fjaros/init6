package com.vilenet.connection

import java.net.InetSocketAddress
import java.util.concurrent.TimeUnit

import akka.actor.{ActorRef, FSM, Props}
import akka.io.Tcp.Received
import com.vilenet.connection.binary.{BinaryPacket, BinaryMessageHandler}
import com.vilenet.ViLeNetActor

import scala.annotation.tailrec
import scala.concurrent.duration.Duration

sealed trait PacketReceiverState
case object ReceivingHeader extends PacketReceiverState
case object ReceivingPacket extends PacketReceiverState

sealed trait ReceiverData
case class ReceivingData(buffer: Array[Byte] = Array[Byte]()) extends ReceiverData

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

class BinaryMessageReceiver(clientAddress: InetSocketAddress, connection: ActorRef) extends ViLeNetActor with FSM[PacketReceiverState, ReceiverData] with DeBuffer {

  val HEADER_BYTE = 0xFF.toByte
  val HEADER_SIZE = 4

  val IN_HEADER_TIMEOUT = Duration(10, TimeUnit.SECONDS)

  val handler = context.actorOf(BinaryMessageHandler(clientAddress, connection))

  startWith(ReceivingHeader, ReceivingData())

  when (ReceivingHeader) {
    case Event(Received(data), ReceivingData(buffer)) =>
      @tailrec
      def constructPackets(data: Array[Byte]): State = {
        val fullLength = data.length + buffer.length
        if (fullLength >= HEADER_SIZE) {
          val fullData: Array[Byte] = buffer ++ data

          if (fullData.head == HEADER_BYTE) {
            val packetId = fullData(1)
            val length = toWord(fullData.drop(2))
            val packet = fullData.slice(HEADER_SIZE, length)

            handler ! BinaryPacket(packetId, packet)

            constructPackets(fullData.drop(length))
          } else {
            stop()
          }
        } else if (fullLength == 0) {
          stay
        } else {
          stay using ReceivingData(buffer ++ data) forMax IN_HEADER_TIMEOUT
        }
      }
      constructPackets(data.toArray)

    case Event(StateTimeout, _) =>
      stop()
  }
}
