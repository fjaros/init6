package com.init6.coders.binary.packets

import akka.util.ByteString
import com.init6.coders.binary.{BinaryPacket, DeBuffer}

import scala.util.Try

/**
  * Created by filip on 12/14/15.
  */
object SidCreateAccount2 extends BinaryPacket {

  override val PACKET_ID = Packets.SID_CREATEACCOUNT2

  val RESULT_ACCOUNT_CREATED = 0x00
  val RESULT_NAME_TOO_SHORT = 0x01
  val RESULT_INVALID_CHARACTERS = 0x02
  val RESULT_BANNED_WORD = 0x03
  val RESULT_ALREADY_EXISTS = 0x04
  val RESULT_BEING_CREATED = 0x05
  val RESULT_NOT_ENOUGH_ALPHANUMERIC_CHARACTERS = 0x06
  val RESULT_ADJACENT_PUNCTUATION_CHARACTERS = 0x07
  val RESULT_TOO_MANY_PUNCTUATION_CHARACTERS = 0x08

  def apply(result: Int): ByteString = {
    build(
      ByteString.newBuilder
        .putInt(result)
        .putByte(0)
        .result()
    )
  }

  def unapply(data: ByteString): Option[SidCreateAccount2] = {
    Try {
      val debuffer = DeBuffer(data)
      val passwordHash = debuffer.byteArray(20)
      val username = debuffer.string()
      SidCreateAccount2(passwordHash, username)
    }.toOption
  }
}

case class SidCreateAccount2(passwordHash: Array[Byte], username: String)
