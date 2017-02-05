package com.init6.coders.binary

import akka.util.ByteString
import com.init6.Constants._
import com.init6.channels._
import com.init6.coders.Encoder
import com.init6.coders.binary.packets.{SidChatEvent, SidFloodDetected}

/**
 * Created by filip on 10/25/15.
 */
object BinaryChatEncoder extends Encoder {

  private implicit def longToInt(long: Long): Int = long.toInt

  override def apply(data: Any): Option[ByteString] = {
    data match {
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
      case UserBroadcast(user, message) =>
        SidChatEvent(0x06, 0, 0, INIT6, message)
      case UserChannel(user, channel, flags, _) =>
        SidChatEvent(0x07, flags, user.ping, user.name, channel)
      case UserFlags(user) =>
        SidChatEvent(0x09, user.flags, user.ping, user.name, user.client)
      case UserWhisperedTo(user, message) =>
        SidChatEvent(0x0A, user.flags, user.ping, user.name, message)
      case UserInfo(message) =>
        SidChatEvent(0x12, 0,0, "", message)
      case UserInfoArray(messages) =>
        handleArrayEvent(0x12, messages)
      case ServerTopicArray(messages) =>
        handleArrayEvent(0x12, messages)
      case UserError(message) =>
        SidChatEvent(0x13, 0,0, "", message)
      case UserErrorArray(messages) =>
        handleArrayEvent(0x13, messages)
      case UserEmote(user, message) =>
        SidChatEvent(0x17, user.flags, user.ping, user.name, message)
      case UserFlooded =>
        SidChatEvent(0x13, 0, 0, "", FLOODED_OFF) ++ SidFloodDetected()
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
