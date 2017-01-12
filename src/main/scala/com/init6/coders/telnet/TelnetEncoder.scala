package com.init6.coders.telnet

import akka.util.ByteString
import com.init6.Constants._
import com.init6.channels._
import com.init6.coders.Encoder

import scala.annotation.switch

/**
 * Created by filip on 9/20/15.
 */
object TelnetEncoder extends Encoder {

  override implicit protected def encode(data: String): ByteString = super.encode(s"$data\r\n")

  def apply(data: String): ByteString = data

  override def apply(data: Any): Option[ByteString] = {
    data match {
      case UserIn(user) =>
        s"1001 USER ${user.name} ${encodeFlags(user.flags)} [${encodeClient(user.client)}]"
      case UserJoined(user) =>
        s"1002 JOIN ${user.name} ${encodeFlags(user.flags)} [${encodeClient(user.client)}]"
      case UserLeft(user) =>
        s"1003 LEAVE ${user.name} ${encodeFlags(user.flags)}"
      case UserWhisperedFrom(user, message) =>
        s"1004 WHISPER ${user.name} ${encodeFlags(user.flags)} ${'"'}$message${'"'}"
      case UserTalked(user, message) =>
        s"1005 TALK ${user.name} ${encodeFlags(user.flags)} ${'"'}$message${'"'}"
      case UserBroadcast(user, message) =>
        s"1006 BROADCAST ${'"'}$message${'"'}"
      case UserChannel(user, channel, _) =>
        s"1007 CHANNEL ${'"'}$channel${'"'}"
      case UserFlags(user) =>
        s"1009 USER ${user.name} ${encodeFlags(user.flags)} [${encodeClient(user.client)}]"
      case UserWhisperedTo(user, message) =>
        s"1010 WHISPER ${user.name} ${encodeFlags(user.flags)} ${'"'}$message${'"'}"
      case UserInfo(message) =>
        s"1018 INFO ${'"'}$message${'"'}"
      case UserInfoArray(messages) =>
        messages
          .map(message => encode(s"1018 INFO ${'"'}$message${'"'}"))
          .reduceLeft(_ ++ _)
      case UserError(message) =>
        s"1019 ERROR ${'"'}$message${'"'}"
      case UserErrorArray(messages) =>
        messages
          .map(message => encode(s"1019 ERROR ${'"'}$message${'"'}"))
          .reduceLeft(_ ++ _)
      case UserEmote(user, message) =>
        s"1023 EMOTE ${user.name} ${encodeFlags(user.flags)} ${'"'}$message${'"'}"
      case UserNull =>
        "2000 NULL"
      case UserName(name) =>
        s"2010 NAME $name"
      case UserFlooded =>
        s"1019 ERROR ${'"'}$FLOODED_OFF${'"'}"
      case _ =>
        None
    }
  }

  private def encodeClient(client: String): String = {
    val sb = new StringBuilder()
    for (i <- client.length - 1 to 0 by -1) {
      sb.append(client.charAt(i))
    }
    sb.toString()
  }

  private def encodeFlags(data: Long): String = {
    val hex = data.toHexString
    (hex.length: @switch) match {
      case 1 => s"000$hex"
      case 2 => s"00$hex"
      case 3 => s"0$hex"
      case 4 => hex
      case _ => "0000"
    }
  }
}
