package com.vilenet.coders.binary.packets

import akka.util.ByteString
import com.vilenet.coders.binary.BinaryPacket

/**
 * Created by filip on 10/28/15.
 */
object SidPing extends BinaryPacket {

  override val PACKET_ID: Byte = Packets.SID_PING

  def apply(cookie: Int): ByteString = {
    build(
      ByteString.newBuilder
        .putInt(cookie)
        .result()
    )
  }
}
