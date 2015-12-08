package com.vilenet.coders.binary

import akka.util.ByteString
import com.vilenet.channels._
import com.vilenet.coders.Encoder
import com.vilenet.coders.binary.packets.SidChatEvent

import scala.annotation.switch

/**
 * Created by filip on 10/25/15.
 */
object BinaryChatEncoder extends Encoder {

  private implicit def longToInt(long: Long): Int = long.toInt

  override def apply(data: Any): Option[ByteString] = {
    (data: @switch) match {
      case UserIn(user) =>
        SidChatEvent(0x01, user.flags, user.ping, user.name, user.client)
      case UserJoined(user) =>
        SidChatEvent(0x02, user.flags, user.ping, user.name, user.client)
      case UserLeft(user) =>
        SidChatEvent(0x03, user.flags, user.ping, user.name)
      case UserWhisperedFrom(user, message) =>
        SidChatEvent(0x04, user.flags, user.ping, user.name, message)
      case UserTalked(user, message) =>
        SidChatEvent(0x05, user.flags, user.ping, user.name, message)
      case UserBroadcast(message) =>
        SidChatEvent(0x06, 0, 0, "", message)
      case UserChannel(user, channel, _) =>
        SidChatEvent(0x07, 0, 0, "", channel)
      case UserFlags(user) =>
        SidChatEvent(0x09, user.flags, user.ping, user.name, user.client)
      case UserWhisperedTo(user, message) =>
        SidChatEvent(0x0A, user.flags, user.ping, user.name, message)
      case UserInfo(message) =>
        SidChatEvent(0x12, 0,0, "", message)
      case UserInfoArray(messages) =>
        handleArrayEvent(0x12, messages)
      case UserError(message) =>
        SidChatEvent(0x13, 0,0, "", message)
      case UserErrorArray(messages) =>
        handleArrayEvent(0x13, messages)
      case UserEmote(user, message) =>
        SidChatEvent(0x17, user.flags, user.ping, user.name, message)
      case _ =>
        None
    }
  }

  def handleArrayEvent(packetId: Byte, messages: Array[String]) = {
    messages
      .map(SidChatEvent(packetId, 0, 0, "", _))
      .reduceLeft(_ ++ _)
  }
}
