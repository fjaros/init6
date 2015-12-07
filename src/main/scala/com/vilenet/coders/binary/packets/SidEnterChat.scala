package com.vilenet.coders.binary.packets

import akka.util.ByteString
import com.vilenet.coders.binary.BinaryPacket

/**
 * Created by filip on 10/25/15.
 */
object SidEnterChat extends BinaryPacket {

  override val PACKET_ID = Packets.SID_ENTERCHAT

  def apply(username: String, oldUsername: String, productId: String): ByteString = {
    build(
      ByteString.newBuilder
        .putBytes(username)
        .putBytes(productId)
        .putBytes(oldUsername)
        .result()
    )
  }
}
