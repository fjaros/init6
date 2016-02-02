package com.vilenet.connection

import java.net.InetSocketAddress
import java.util.concurrent.TimeUnit

import akka.actor.{PoisonPill, ActorRef, FSM, Props}
import akka.io.Tcp
import akka.io.Tcp.{PeerClosed, Received}
import akka.util.ByteString
import com.vilenet.connection.binary.{BinaryPacket, BinaryMessageHandler}
import com.vilenet.{Constants, ViLeNetActor}

import scala.annotation.tailrec
import scala.concurrent.duration.Duration

sealed trait PacketReceiverState
case object ReceivingHeader extends PacketReceiverState
case object ReceivingPacket extends PacketReceiverState

sealed trait ReceiverData
case class ReceivingData(buffer: Array[Byte] = Array[Byte]()) extends ReceiverData

/**
 * Created by filip on 10/25/15.
 */
object BinaryMessageReceiver {
  def apply(clientAddress: InetSocketAddress, connection: ActorRef) = Props(new BinaryMessageReceiver(clientAddress, connection))
}

class BinaryMessageReceiver(clientAddress: InetSocketAddress, connection: ActorRef) extends ViLeNetActor with FSM[PacketReceiverState, ReceiverData] {

  val HEADER_BYTE = 0xFF.toByte
  val HEADER_SIZE = 4

  val IN_HEADER_TIMEOUT = Duration(10, TimeUnit.SECONDS)

  val handler = context.actorOf(BinaryMessageHandler(clientAddress, connection))

  startWith(ReceivingHeader, ReceivingData())

  when (ReceivingHeader) {
    case Event(Received(data), ReceivingData(buffer)) =>
      @tailrec
      def constructPackets(data: Array[Byte]): State = {
        val dataLen = data.length
        if (dataLen >= HEADER_SIZE) {
          val fullData: Array[Byte] = data

          if (data.head == HEADER_BYTE) {
            val packetId = data(1)
            val length = (data(3) << 8 & 0xFF00 | data(2) & 0xFF).toShort

            if (dataLen >= length) {
              val packet = ByteString(fullData.slice(HEADER_SIZE, length))

              handler ! BinaryPacket(packetId, packet)

              constructPackets(fullData.drop(length))
            } else {
              stay using ReceivingData(fullData)
            }
          } else {
            stop()
          }
        } else if (dataLen == 0) {
          stay using ReceivingData()
        } else {
          stay using ReceivingData(data) forMax IN_HEADER_TIMEOUT
        }
      }
      constructPackets(buffer ++ data.toArray[Byte])

    case Event(StateTimeout, _) =>
      stop()
  }
}
