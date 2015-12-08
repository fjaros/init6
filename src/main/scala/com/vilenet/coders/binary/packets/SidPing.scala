package com.vilenet.coders.binary.packets

import akka.util.ByteString
import com.vilenet.coders.binary.{DeBuffer, BinaryPacket}

import scala.util.Try

/**
 * Created by filip on 10/28/15.
 */
object SidPing extends BinaryPacket {

  case class SidPing(cookie: Int)

  override val PACKET_ID: Byte = Packets.SID_PING

  def apply(cookie: Int): ByteString = {
    build(
      ByteString.newBuilder
        .putInt(cookie)
        .result()
    )
  }

  def unapply(data: ByteString): Option[SidPing] = {
    Try {
      val debuffer = DeBuffer(data)
      val cookie = debuffer.dword()
      SidPing(cookie)
    }.toOption
  }
}
