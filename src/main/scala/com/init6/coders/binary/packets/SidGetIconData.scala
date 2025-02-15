package com.init6.coders.binary.packets

import akka.util.ByteString
import com.init6.coders.binary.BinaryPacket

import java.io.File

object SidGetIconData extends BinaryPacket {

  case class SidGetIconData()

  val LAST_MODIFIED = new File("bnftp/icons.bni").lastModified()

  override val PACKET_ID = Packets.SID_GETICONDATA

  def apply() = {
    build(
      ByteString.newBuilder
        .putLong(LAST_MODIFIED)
        .putBytes("icons.bni")
        .result()
    )
  }

  def unapply(data: ByteString): Option[SidGetIconData] = {
    Some(SidGetIconData())
  }
}

