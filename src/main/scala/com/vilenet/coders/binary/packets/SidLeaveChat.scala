package com.vilenet.coders.binary.packets

import akka.util.ByteString
import com.vilenet.coders.binary.BinaryPacket

/**
  * Created by filip on 12/13/15.
  */
object SidLeaveChat extends BinaryPacket {

  case class SidLeaveChat()

  override val PACKET_ID: Byte = Packets.SID_LEAVECHAT

  def apply(): ByteString = {
    build(
      ByteString.newBuilder
        .result()
    )
  }

  def unapply(data: ByteString): Option[SidLeaveChat] = {
    if (data.isEmpty) {
      Some(SidLeaveChat())
    } else {
      None
    }
  }
}
