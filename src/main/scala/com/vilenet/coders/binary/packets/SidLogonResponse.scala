package com.vilenet.coders.binary.packets

import akka.util.ByteString
import com.vilenet.coders.binary.BinaryPacket

/**
 * Created by filip on 10/25/15.
 */
object SidLogonResponse extends BinaryPacket {

  override val PACKET_ID: Byte = 0x3A

  def apply(): ByteString = {
    build(
      ByteString.newBuilder
        .putInt(0)
        .result()
    )
  }
}