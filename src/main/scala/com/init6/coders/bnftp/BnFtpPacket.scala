package com.init6.coders.bnftp

import akka.util.ByteString

import java.io.{File, RandomAccessFile}
import java.nio.ByteOrder

trait BnFtpPacket {

  implicit val byteOrder = ByteOrder.LITTLE_ENDIAN

  val PACKET_HEADER_LENGTH: Short = 2

  implicit def stringToNTBytes(string: String): Array[Byte] = {
    Array.newBuilder[Byte]
      .++=(string.map(_.toByte))
      .+=(0)
      .result()
  }

  def build(data: ByteString, fileStartPosition: Int, file: File) = {
    val randomAccessFile = new RandomAccessFile(file, "r")
    val readLength = file.length().toInt - fileStartPosition
    val b = new Array[Byte](readLength)
    randomAccessFile.readFully(b, fileStartPosition, readLength)
    randomAccessFile.close()
    ByteString.newBuilder
      .putShort(data.length + PACKET_HEADER_LENGTH)
      .append(data)
      .putBytes(b)
      .result()
  }
}
