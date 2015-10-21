package com.vilenet.coders.telnet

import akka.util.ByteString
import com.vilenet.channels._
import com.vilenet.coders.AkkaEncoder
import com.vilenet.connection.JustLoggedIn

import scala.annotation.switch

/**
 * Created by filip on 9/20/15.
 */
object TelnetEncoder extends AkkaEncoder {

  override implicit protected def encode(data: String): ByteString = super.encode(s"$data\r\n")
  implicit private def encodeToOption(data: String): Option[ByteString] = Some(data)

  def apply(data: String): ByteString = data

  def apply(data: Any): Option[ByteString] = {
    (data: @switch) match {
      case UserIn(user) =>
        s"1001 USER ${user.name} ${encodeFlags(user.flags)} [${user.client}]"
      case UserJoined(user) =>
        s"1002 JOIN ${user.name} ${encodeFlags(user.flags)} [${user.client}]"
      case UserLeft(user) =>
        s"1003 LEAVE ${user.name}"
      case UserWhisperedFrom(user, message) =>
        s"1004 WHISPER ${user.name} ${encodeFlags(user.flags)} ${'"'}$message${'"'}"
      case UserTalked(user, message) =>
        s"1005 TALK ${user.name} ${encodeFlags(user.flags)} ${'"'}$message${'"'}"
      case UserBroadcast(message) =>
        s"1006 BROADCAST ${'"'}$message${'"'}"
      case UserChannel(user, channel) =>
        s"1007 CHANNEL ${'"'}$channel${'"'}"
      case UserFlags(user) =>
        s"1009 USER ${user.name} ${encodeFlags(user.flags)} [${user.client}]"
      case UserWhisperedTo(user, message) =>
        s"1010 WHISPER ${user.name} ${encodeFlags(user.flags)} ${'"'}$message${'"'}"
      case UserInfo(message) =>
        s"1018 INFO ${'"'}$message${'"'}"
      case UserError(message) =>
        s"1019 ERROR ${'"'}$message${'"'}"
      case UserEmote(user, message) =>
        s"1023 EMOTE ${user.name} ${encodeFlags(user.flags)} ${'"'}$message${'"'}"
      case UserNull =>
        "2000 NULL"
      case UserName(name) =>
        s"2010 NAME $name"
      case _ =>
        None
    }
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
