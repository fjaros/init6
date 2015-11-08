package com.vilenet.coders.binary.packets

import akka.util.ByteString
import com.vilenet.coders.binary.BinaryPacket

/**
 * Created by filip on 10/28/15.
 */
object SidLogonChallengeEx extends BinaryPacket {

  override val PACKET_ID: Byte = 0x1D

  def apply(): ByteString = {
    build(
      ByteString.newBuilder
        .putInt(0xdeadbeef)
        .putInt(0xdeadbeef)
        .result()
    )
  }
}
