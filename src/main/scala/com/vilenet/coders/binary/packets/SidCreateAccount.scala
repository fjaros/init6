package com.vilenet.coders.binary.packets

import akka.util.ByteString
import com.vilenet.coders.binary.{DeBuffer, BinaryPacket}

import scala.util.Try

/**
  * Created by filip on 12/14/15.
  */
object SidCreateAccount extends BinaryPacket {

  override val PACKET_ID = Packets.SID_CREATEACCOUNT

  val RESULT_FAILED = 0x00
  val RESULT_ACCOUNT_CREATED = 0x01

  def apply(result: Int): ByteString = {
    build(
      ByteString.newBuilder
        .putInt(result)
        .result()
    )
  }

  def unapply(data: ByteString): Option[SidCreateAccount] = {
    Try {
      val debuffer = DeBuffer(data)
      val passwordHash = debuffer.byteArray(20)
      val username = debuffer.string()
      SidCreateAccount(passwordHash, username)
    }.toOption
  }
}

case class SidCreateAccount(passwordHash: Array[Byte], username: String)
