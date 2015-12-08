package com.vilenet.coders.binary.packets

import akka.util.ByteString
import com.vilenet.coders.binary.{DeBuffer, BinaryPacket}

import scala.util.Try

/**
 * Created by filip on 10/28/15.
 */
object SidReportVersion extends BinaryPacket {

  override val PACKET_ID: Byte = Packets.SID_REPORTVERSION

  val RESULT_FAILED_VERSION_CHECK = 0x00
  val RESULT_OLD_GAME_VERSION = 0x01
  val RESULT_SUCCESS = 0x02
  val RESULT_REINSTALL_REQUIRED = 0x03

  def apply(result: Int): ByteString = {
    build(
      ByteString.newBuilder
        .putInt(result)
        .putByte(0)
        .result()
    )
  }

  def unapply(data: ByteString): Option[SidReportVersion] = {
    Try {
      val debuffer = DeBuffer(data)
      debuffer.skip(4)
      val productId = debuffer.byteArrayAsString(4)
      val versionByte = debuffer.byte()
      SidReportVersion(productId, versionByte)
    }.toOption
  }
}

case class SidReportVersion(productId: String, versionByte: Byte)
