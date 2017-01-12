package com.init6.coders.binary.packets

import akka.util.ByteString
import com.init6.coders.binary.BinaryPacket

import scala.util.Try

/**
  * Created by filip on 12/16/16.
  */
object SidCdKey extends BinaryPacket {

  override val PACKET_ID = Packets.SID_CDKEY

  def apply(): ByteString = {
    build(
      ByteString.newBuilder
        .putInt(1)
        .putByte(0)
        .result()
    )
  }

  case class SidCdKey()

  def unapply(data: ByteString): Option[SidCdKey] = {
    Try {
      SidCdKey()
    }.toOption
  }
}
