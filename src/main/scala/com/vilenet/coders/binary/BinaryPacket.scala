package com.vilenet.coders.binary

import java.nio.ByteOrder

import akka.util.ByteString

/**
 * Created by filip on 10/25/15.
 */
trait BinaryPacket {

  implicit val byteOrder = ByteOrder.LITTLE_ENDIAN

  val PACKET_HEADER = 0xFF.toByte
  val PACKET_HEADER_LENGTH = 4.toShort

  val PACKET_ID: Byte

  implicit def stringToNTBytes(string: String): Array[Byte] = {
    Array.newBuilder[Byte]
      .++=(string.map(_.toByte))
      .+=(0)
      .result()
  }

  def build(data: ByteString) = {
    ByteString.newBuilder
      .putByte(PACKET_HEADER)
      .putByte(PACKET_ID)
      .putShort(data.length + PACKET_HEADER_LENGTH)
      .append(data)
      .result()
  }
}
