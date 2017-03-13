package com.init6.coders.binary.packets

import akka.util.ByteString
import com.init6.coders.binary.{BinaryPacket, DeBuffer}

import scala.util.Try

/**
 * Created by filip on 10/25/15.
 */
object SidEnterChat extends BinaryPacket {

  override val PACKET_ID = Packets.SID_ENTERCHAT

  def apply(username: String, oldUsername: String, productId: String): ByteString = {
    build(
      ByteString.newBuilder
        .putBytes(username)
        .putBytes(productId)
        .putBytes(oldUsername)
        .result()
    )
  }

  case class SidEnterChat(username: String)

  def unapply(data: ByteString): Option[SidEnterChat] = {
    Try {
      val debuffer = DeBuffer(data)
      val username = debuffer.string()
      SidEnterChat(username)
    }.toOption
  }
}
