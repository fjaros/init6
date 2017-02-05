package com.init6.coders.binary.packets

import akka.util.ByteString
import com.init6.coders.binary.BinaryPacket

/**
  * Created by filip on 2/4/17.
  */
object SidFriendsList extends BinaryPacket {

  case class SidFriendsList()

  override val PACKET_ID = Packets.SID_FRIENDSLIST

  def apply() = {
    build(
      ByteString.newBuilder
        .putByte(0)
        .result()
    )
  }

  def unapply(data: ByteString): Option[SidFriendsList] = {
    if (data.isEmpty) {
      Some(SidFriendsList())
    } else {
      None
    }
  }
}
