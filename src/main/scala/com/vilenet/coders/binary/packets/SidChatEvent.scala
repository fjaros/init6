package com.vilenet.coders.binary.packets

import akka.util.ByteString
import com.vilenet.coders.binary.BinaryPacket

/**
 * Created by filip on 10/25/15.
 */
object SidChatEvent extends BinaryPacket {

  override val PACKET_ID = Packets.SID_CHATEVENT

  def apply(eventId: Int, userFlags: Int, ping: Int, username: String, text: String = "") = {
    build(
      ByteString.newBuilder
        .putInt(eventId)
        .putInt(userFlags)
        .putInt(ping)
        .putInt(0)
        .putInt(0)
        .putInt(0)
        .putBytes(username)
        .putBytes(text)
        .result()
    )
  }
}
