package com.init6.coders.binary.packets

import akka.util.ByteString
import com.init6.coders.binary.{BinaryPacket, DeBuffer}

import java.io.File
import scala.util.Try

object SidGetFileTime extends BinaryPacket {

  case class SidGetFileTime(requestId: Int, fileName: String)

  override val PACKET_ID = Packets.SID_GETFILETIME

  def apply(requestId: Int, fileName: String) = {
    build(
      ByteString.newBuilder
        .putInt(requestId)
        .putInt(0)
        .putLong(new File("bnftp/" + fileName).lastModified())
        .putBytes(fileName)
        .result()
    )
  }

  def unapply(data: ByteString): Option[SidGetFileTime] = {
    Try {
      val debuffer = DeBuffer(data)
      val requestId = debuffer.dword()
      debuffer.skip(4)
      val fileName = debuffer.string()
      SidGetFileTime(requestId, fileName)
    }.toOption
  }
}

