package com.vilenet.coders.binary.packets

import akka.util.ByteString
import com.vilenet.coders.binary.{DeBuffer, BinaryPacket}

import scala.util.Try

/**
 * Created by filip on 10/25/15.
 */
object SidAuthCheck extends BinaryPacket {

  override val PACKET_ID = Packets.SID_AUTH_CHECK

  def apply(): ByteString = {
    build(
      ByteString.newBuilder
        .putInt(0)
        .putByte(0)
        .result()
    )
  }

  def unapply(data: ByteString): Option[SidAuthCheck] = {
    Try {
      val debuffer = DeBuffer(data)
      SidAuthCheck(debuffer.dword())
    }.toOption
  }
}

// Might expand in future to do real hashing...
case class SidAuthCheck(clientToken: Int)
