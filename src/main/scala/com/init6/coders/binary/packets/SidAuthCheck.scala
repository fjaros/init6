package com.init6.coders.binary.packets

import akka.util.ByteString
import com.init6.coders.binary.{DeBuffer, BinaryPacket}

import scala.util.Try

/**
 * Created by filip on 10/25/15.
 */
object SidAuthCheck extends BinaryPacket {

  override val PACKET_ID = Packets.SID_AUTH_CHECK

  val RESULT_SUCCESS = 0x00
  val RESULT_OLD_GAME_VERSION = 0x100
  val RESULT_INVALID_VERSION = 0x101
  val RESULT_VERSION_DOWNGRADED = 0x102

  def apply(result: Int): ByteString = {
    build(
      ByteString.newBuilder
        .putInt(result)
        .putByte(0)
        .result()
    )
  }

  // Might expand in future to do real hashing...
  case class SidAuthCheck(clientToken: Int)

  def unapply(data: ByteString): Option[SidAuthCheck] = {
    Try {
      val debuffer = DeBuffer(data)
      SidAuthCheck(debuffer.dword())
    }.toOption
  }
}

