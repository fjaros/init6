package com.init6.coders.binary.packets

import akka.util.ByteString
import com.init6.coders.binary.{BinaryPacket, DeBuffer}

import scala.util.Try

/**
  * Created by fjaros on 12/19/16.
  */
object SidChangePassword extends BinaryPacket {

  override val PACKET_ID = Packets.SID_CHANGEPASSWORD

  val RESULT_FAILED = 0x00
  val RESULT_SUCCESS = 0x01

  def apply(result: Int): ByteString = {
    build(
      ByteString.newBuilder
        .putInt(result)
        .result()
    )
  }

  case class SidChangePassword(
    clientToken: Int, serverToken: Int,
    oldPasswordHash: Array[Byte], newPasswordHash: Array[Byte],
    username: String
  )

  def unapply(data: ByteString): Option[SidChangePassword] = {
    Try {
      val debuffer = DeBuffer(data)
      SidChangePassword(
        debuffer.dword(), debuffer.dword(),
        debuffer.byteArray(20), debuffer.byteArray(20),
        debuffer.string()
      )
    }.toOption
  }
}
