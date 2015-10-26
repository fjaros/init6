package com.vilenet.coders.binary.packets

import akka.util.ByteString
import com.vilenet.coders.binary.BinaryPacket

/**
 * Created by filip on 10/25/15.
 */
object SidAuthCheck extends BinaryPacket {

  def apply(): ByteString = {
    build(ID_SID_AUTH_CHECK,
      ByteString.newBuilder
        .putLong(0)
        .putByte(0)
        .result()
    )
  }
}