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

  val buffer = ArrayBuffer[Byte]()

  def parsePacket(data: ByteString): Array[T]
}

class ChatReceiver extends PacketReceiver[ByteString] {

  def parsePacket(data: ByteString): Array[ByteString] = {
    @tailrec
    def parsePacketInternal(data: ByteString, result: ArrayBuffer[ByteString]): ArrayBuffer[ByteString] = {
      val readData = data.takeWhile(b => b != '\r' && b != '\n')
      // sanity check
      if (!ChatValidator(readData)) {
        throw new IllegalArgumentException("Message contains illegal characters")
      }

      if (data.length == readData.length) {
        // Split packet
        buffer ++= readData
      } else {
        // End of packet found
        if (buffer.nonEmpty) {
          result += ByteString(buffer.toArray[Byte] ++ readData.toArray[Byte])
          buffer.clear()
        } else if (readData.nonEmpty) {
          result += readData
        }
      }
      val restOfData = data.drop(readData.length).dropWhile(b => b == '\r' || b == '\n')
      if (restOfData.nonEmpty) {
        parsePacketInternal(restOfData, result)
      } else {
        result
      }
    }
    parsePacketInternal(data, new ArrayBuffer()).toArray
  }
}

class BinaryReceiver extends PacketReceiver[BinaryPacket] {

  val HEADER_BYTE = 0xFF.toByte
  val HEADER_SIZE = 4

  def parsePacket(data: ByteString): Array[BinaryPacket] = {
    @tailrec
    def parsePacketInternal(data: ByteString, result: ArrayBuffer[BinaryPacket]): ArrayBuffer[BinaryPacket] = {
      val dataLen = data.length
      if (dataLen >= HEADER_SIZE) {
        if (data.head == HEADER_BYTE) {
          val packetId = data(1)
          val length = (data(3) << 8 & 0xFF00 | data(2) & 0xFF).toShort

          if (dataLen >= length) {
            val packet = data.slice(HEADER_SIZE, length)

            result += BinaryPacket(packetId, packet)

            parsePacketInternal(data.drop(length), result)
          } else {
            buffer ++= data
            result
          }
        } else {
          throw new IllegalArgumentException("Header identifier is invalid")
        }
      } else {
        buffer ++= data
        result
      }
    }
    parsePacketInternal(data, new ArrayBuffer()).toArray
  }
}
