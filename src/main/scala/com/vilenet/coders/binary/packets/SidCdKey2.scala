package com.vilenet.coders.binary.packets

import akka.util.ByteString
import com.vilenet.coders.binary.{BinaryPacket, DeBuffer}

import scala.util.Try

/**
  * Created by filip on 12/15/16.
  */
object SidCdKey2 extends BinaryPacket {

  override val PACKET_ID = Packets.SID_CDKEY2

  def apply(): ByteString = {
    build(
      ByteString.newBuilder
        .putInt(1)
        .putByte(0)
        .result()
    )
  }

  case class SidCdKey2()

  def unapply(data: ByteString): Option[SidCdKey2] = {
    Try {
      SidCdKey2()
    }.toOption
  }
}
