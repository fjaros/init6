package com.init6.coders.binary.packets

import akka.util.ByteString
import com.init6.coders.binary.BinaryPacket

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
