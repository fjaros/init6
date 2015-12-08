package com.vilenet.coders.binary.packets

import akka.util.ByteString
import com.vilenet.coders.binary.BinaryPacket

/**
 * Created by filip on 10/28/15.
 */
object SidLogonChallenge extends BinaryPacket {

  override val PACKET_ID = Packets.SID_LOGONCHALLENGE

  def apply(serverToken: Int): ByteString = {
    build(
      ByteString.newBuilder
        .putInt(serverToken)
        .result()
    )
  }
}
