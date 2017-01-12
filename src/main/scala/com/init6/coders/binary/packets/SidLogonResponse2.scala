package com.init6.coders.binary.packets

import akka.util.ByteString
import com.init6.coders.binary.{DeBuffer, BinaryPacket}

import scala.util.Try

/**
 * Created by filip on 10/25/15.
 */
object SidLogonResponse2 extends BinaryPacket {

  override val PACKET_ID = Packets.SID_LOGONRESPONSE2

  val RESULT_SUCCESS = 0x00
  val RESULT_DOES_NOT_EXIST = 0x01
  val RESULT_INVALID_PASSWORD = 0x02
  val RESULT_ACCOUNT_CLOSED = 0x06

  def apply(result: Int, reason: String = ""): ByteString = {
    build(
      ByteString.newBuilder
        .putInt(result)
        .putBytes(reason)
        .result()
    )
  }

  def unapply(data: ByteString): Option[SidLogonResponse2] = {
    Try {
      val debuffer = DeBuffer(data)
      val clientToken = debuffer.dword()
      val serverToken = debuffer.dword()
      val passwordHash = debuffer.byteArray(20)
      val username = debuffer.string()
      SidLogonResponse2(clientToken, serverToken, passwordHash, username)
    }.toOption
  }
}

case class SidLogonResponse2(clientToken: Int, serverToken: Int, passwordHash: Array[Byte], username: String)
