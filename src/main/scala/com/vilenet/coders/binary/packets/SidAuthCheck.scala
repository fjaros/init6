package com.vilenet.coders.binary.packets

import akka.util.ByteString
import com.vilenet.coders.binary.BinaryPacket

/**
 * Created by filip on 10/25/15.
 */
object SidAuthCheck extends BinaryPacket {

  override val PACKET_ID = Packets.SID_AUTH_CHECK

  def apply(): ByteString = {
    build(
      ByteString.newBuilder
        .putInt(0)
        .putByte(0)
        .result()
    )
  }
}
