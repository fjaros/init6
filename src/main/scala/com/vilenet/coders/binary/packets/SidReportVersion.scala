package com.vilenet.coders.binary.packets

import akka.util.ByteString
import com.vilenet.coders.binary.BinaryPacket

/**
 * Created by filip on 10/28/15.
 */
object SidReportVersion extends BinaryPacket {

  override val PACKET_ID: Byte = 0x07

  def apply(): ByteString = {
    build(
      ByteString.newBuilder
        .putInt(2)
        .putByte(0)
        .result()
    )
  }
}
