package com.vilenet.coders.binary.packets

import akka.util.ByteString
import com.vilenet.coders.binary.{DeBuffer, BinaryPacket}

import scala.util.Try

/**
 * Created by filip on 10/25/15.
 */
object SidLogonResponse extends BinaryPacket {

  override val PACKET_ID = Packets.SID_LOGONRESPONSE

  val RESULT_INVALID_PASSWORD = 0x00
  val RESULT_SUCCESS = 0x01

  def apply(result: Int): ByteString = {
    build(
      ByteString.newBuilder
        .putInt(result)
        .result()
    )
  }

  def unapply(data: ByteString): Option[SidLogonResponse] = {
    Try {
      val debuffer = DeBuffer(data)
      val clientToken = debuffer.dword()
      val serverToken = debuffer.dword()
      val passwordHash = debuffer.byteArray(20)
      val username = debuffer.string()
      SidLogonResponse(clientToken, serverToken, passwordHash, username)
    }.toOption
  }
}

case class SidLogonResponse(clientToken: Int, serverToken: Int, passwordHash: Array[Byte], username: String)
