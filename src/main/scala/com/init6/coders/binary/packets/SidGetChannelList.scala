package com.init6.coders.binary.packets

import akka.util.ByteString
import com.init6.coders.binary.BinaryPacket

/**
  * Created by filip on 2/4/17.
  */
object SidGetChannelList extends BinaryPacket {

  case class SidGetChannelList()

  override val PACKET_ID = Packets.SID_GETCHANNELLIST

  def apply() = {
    build(
      ByteString.newBuilder
        .putBytes("init 6")
        .putByte(0)
        .result()
    )
  }

  def unapply(data: ByteString): Option[SidGetChannelList] = {
    Some(SidGetChannelList())
  }
}
