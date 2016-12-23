package com.vilenet.coders.binary.packets

import akka.util.ByteString
import com.vilenet.coders.binary.BinaryPacket

/**
  * Created by fjaros on 12/23/16.
  */
object SidFloodDetected extends BinaryPacket {

  override val PACKET_ID = Packets.SID_FLOODDETECTED

  def apply(): ByteString = {
    build(
      ByteString.newBuilder
        .result()
    )
  }
}
