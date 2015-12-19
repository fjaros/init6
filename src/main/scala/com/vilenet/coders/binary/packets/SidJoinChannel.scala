package com.vilenet.coders.binary.packets

import akka.util.ByteString
import com.vilenet.coders.binary.{DeBuffer, BinaryPacket}

import scala.util.Try

/**
  * Created by filip on 12/7/15.
  */
object SidJoinChannel extends BinaryPacket {

  override val PACKET_ID = Packets.SID_JOINCHANNEL

  case class SidJoinChannel(joinFlag: Int, channel: String)

  def unapply(data: ByteString): Option[SidJoinChannel] = {
    Try {
      val debuffer = DeBuffer(data)
      val joinFlag = debuffer.dword()
      val channel = debuffer.string()
      SidJoinChannel(joinFlag, channel)
    }.toOption
  }
}