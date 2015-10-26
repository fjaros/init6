package com.vilenet.coders.binary.packets

import akka.util.ByteString
import com.vilenet.coders.binary.BinaryPacket

/**
 * Created by filip on 10/25/15.
 */
object SidEnterChat extends BinaryPacket {

  def apply(username: String, productId: String): ByteString = {
    build(ID_SID_ENTER_CHAT,
      ByteString.newBuilder
        .putBytes(username)
        .putBytes(productId)
        .putBytes(username)
        .result()
    )
  }
}
