package com.vilenet.coders.binary.packets

import akka.util.ByteString
import com.vilenet.coders.binary.{DeBuffer, BinaryPacket}

import scala.util.Try

/**
 * Created by filip on 10/25/15.
 */
object SidAuthInfo extends BinaryPacket {

  override val PACKET_ID = Packets.SID_AUTH_INFO

  def apply(): ByteString = {
    build(
      ByteString.newBuilder
        .putInt(0)
        .putInt(0xdeadbeef)
        .putInt(0xdeadbeef)
        .putInt(0x4341AC00)
        .putInt(0x01C50B25)
        .putBytes("IX86ver3.mpq")
        .putBytes("A=125933019 B=665814511 C=736475113 4 A=A+S B=B^C C=C^A A=A^B")
        .result()
    )
  }

  def unapply(data: ByteString): Option[SidAuthInfo] = {
    Try {
      val debuffer = DeBuffer(data)
      val productId = debuffer.byteArray(4)
      val verbyte = debuffer.byte(8)
      SidAuthInfo(new String(productId), verbyte)
    }.toOption
  }
}

case class SidAuthInfo(productId: String, verByte: Byte)