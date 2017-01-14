package com.init6.coders.chat1

import akka.util.ByteString
import com.init6.Constants._
import com.init6.channels._
import com.init6.coders.Encoder

/**
  * Created by filip on 1/10/16.
  */
object Chat1Encoder extends Encoder {

  override implicit protected def encode(data: String): ByteString = super.encode(s"$data\r\n")

  def apply(data: String): ByteString = data

  override def apply(data: Any): Option[ByteString] = {
    data match {
      case LoginOK =>
        s"OK"
      case LoginFailed(reason) =>
        s"FAIL $reason"
      case UserIn(user) =>
        s"USER IN   ${user.flags} ${user.ping} ${user.name} NONE"
      case UserJoined(user) =>
        s"USER JOIN   ${user.flags} ${user.ping} ${user.name} NONE"
      case UserLeft(user) =>
        s"USER LEAVE     ${user.name} "
      case UserWhisperedFrom(user, message) =>
        s"USER WHISPER FROM    ${user.name} $message"
      case ITalked(user, message) =>
        s"USER TALK TO    ${user.name} $message"
      case UserTalked(user, message) =>
        s"USER TALK FROM    ${user.name} $message"
      case UserBroadcast(user, message) =>
        s"SERVER BROADCAST   ${user.name} $message"
      case UserChannel(user, channel, _) =>
        s"CHANNEL JOIN    $channel"
      case UserFlags(user) =>
        s"USER UPDATE   ${user.flags} ${user.ping} ${user.name} NONE"
      case UserWhisperedTo(user, message) =>
        s"USER WHISPER TO    ${user.name} $message"
      case UserInfo(message) =>
        s"SERVER INFO    $message"
      case UserInfoArray(messages) =>
        messages
          .map(message => encode(s"SERVER TOPIC    $message"))
          .reduceLeft(_ ++ _)
      case UserError(message) =>
        s"SERVER ERROR    $message"
      case UserErrorArray(messages) =>
        messages
          .map(message => encode(s"SERVER ERROR    $message"))
          .reduceLeft(_ ++ _)
      case UserEmote(user, message) =>
        s"USER EMOTE     ${user.name} $message"
      case UserNull =>
        "NULL"
      case UserName(name) =>
        None
      case UserPing(cookie) =>
        s"PING $cookie"
      case UserFlooded =>
        s"SERVER ERROR    $FLOODED_OFF"
      case _ =>
        None
    }
  }
}
