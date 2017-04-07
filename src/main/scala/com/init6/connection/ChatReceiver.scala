package com.init6.connection

import akka.util.ByteString
import com.init6.connection.binary.BinaryPacket
import com.init6.utils.ChatValidator

import scala.annotation.tailrec
import scala.collection.mutable.ArrayBuffer

/**
  * Created by filip on 1/10/16.
  */
trait PacketReceiver[T] {

  var buffer = Vector[Byte]()

  def parsePacket(data: ByteString): Seq[T] = {
    buffer ++= data
    parsePacketInternal(new ArrayBuffer)
  }

  protected def parsePacketInternal(result: ArrayBuffer[T]): ArrayBuffer[T]
}

class ChatReceiver extends PacketReceiver[ByteString] {

  @tailrec
  override final protected def parsePacketInternal(result: ArrayBuffer[ByteString]): ArrayBuffer[ByteString] = {
    val packet = buffer.takeWhile(b => b != '\r' && b != '\n')
    // sanity check
    if (!ChatValidator(packet)) {
      throw new IllegalArgumentException("Message contains illegal characters")
    }

    if (buffer.length > packet.length) {
      buffer = buffer.drop(packet.length).dropWhile(b => b == '\r' || b == '\n')
      if (packet.nonEmpty) {
        result += ByteString(packet.toArray)
      }
      parsePacketInternal(result)
    } else {
      result
    }
  }
}

class BinaryReceiver extends PacketReceiver[BinaryPacket] {

  val HEADER_BYTE = 0xFF.toByte
  val HEADER_SIZE = 4

  @tailrec
  override final protected def parsePacketInternal(result: ArrayBuffer[BinaryPacket]): ArrayBuffer[BinaryPacket] = {
    if (buffer.length >= HEADER_SIZE) {
      if (buffer.head == HEADER_BYTE) {
        val packetId = buffer(1)
        val length = (buffer(3) << 8 & 0xFF00 | buffer(2) & 0xFF).toShort

        if (buffer.length >= length) {
          val packet = buffer.slice(HEADER_SIZE, length)

          result += BinaryPacket(packetId, ByteString(packet.toArray))

          buffer = buffer.drop(length)
          if (buffer.nonEmpty) {
            parsePacketInternal(result)
          } else {
            result
          }
        } else {
          result
        }
      } else {
        throw new IllegalArgumentException("Header identifier is invalid")
      }
    } else {
      result
    }
  }
}
