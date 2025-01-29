package com.init6.coders.binary.packets

import akka.util.ByteString
import com.init6.coders.binary.BinaryPacket

object SidGetIconData extends BinaryPacket {

  override val PACKET_ID = Packets.SID_GETICONDATA

  def apply(): ByteString = {
    build(
      ByteString.newBuilder
        .putInt(0)
        .putBytes("icons.bni")
        .result()
    )
  }

  case class SidGetIconData()

  def unapply(data: ByteString): Option[SidGetIconData] = {
    Some(SidGetIconData())
  }
}
