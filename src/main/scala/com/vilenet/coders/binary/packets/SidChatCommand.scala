package com.vilenet.coders.binary.packets

import akka.util.ByteString
import com.vilenet.coders.binary.{DeBuffer, BinaryPacket}

import scala.util.Try

/**
  * Created by filip on 12/7/15.
  */
object SidChatCommand extends BinaryPacket {

  override val PACKET_ID = Packets.SID_CHATCOMMAND

  def unapply(data: ByteString): Option[SidChatCommand] = {
    Try {
      val debuffer = DeBuffer(data)
      SidChatCommand(debuffer.string())
    }.toOption
  }
}

case class SidChatCommand(message: String)