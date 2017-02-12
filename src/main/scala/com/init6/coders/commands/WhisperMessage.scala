package com.init6.coders.commands

import com.init6.Constants._
import com.init6.channels.{UserError, User}

/**
  * Created by filip on 12/16/15.
  */
object WhisperMessage {

  def apply(fromUser: User, command: String): Command = {
    val (toUsername, message) = CommandDecoder.spanBySpace(command)

    if (toUsername.nonEmpty) {
      if (message.nonEmpty) {
        WhisperMessage(fromUser, toUsername, message, sendNotification = true)
      } else {
        UserError(NO_MESSAGE_INPUT)
      }
    } else {
      UserError(NO_WHISPER_USER_INPUT)
    }
  }
}

case class WhisperMessage(override val fromUser: User, toUsername: String, override val message: String, sendNotification: Boolean) extends UserCommand with MessageCommand
case class WhisperToFriendsMessage(fromUser: User, toFriends: Seq[String], message: String) extends Command
