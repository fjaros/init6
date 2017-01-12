package com.init6.coders.binary.packets

import akka.util.ByteString
import com.init6.coders.binary.BinaryPacket

/**
  * Created by filip on 12/13/15.
  */
object SidNull extends BinaryPacket {

  case class SidNull()

  override val PACKET_ID: Byte = Packets.SID_NULL

  def apply(): ByteString = {
    build(
      ByteString.newBuilder
        .result()
    )
  }

  def unapply(data: ByteString): Option[SidNull] = {
    if (data.isEmpty) {
      Some(SidNull())
    } else {
      None
    }
  }
}
