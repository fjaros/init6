package com.vilenet.coders.binary.packets

import akka.util.ByteString
import com.vilenet.coders.binary.BinaryPacket

/**
 * Created by filip on 10/28/15.
 */
object SidLogonChallengeEx extends BinaryPacket {

  override val PACKET_ID = Packets.SID_LOGONCHALLENGEEX

  def apply(serverToken: Int, udpToken: Int = 0xDEADBEEF): ByteString = {
    build(
      ByteString.newBuilder
        .putInt(udpToken)
        .putInt(serverToken)
        .result()
    )
  }
}
