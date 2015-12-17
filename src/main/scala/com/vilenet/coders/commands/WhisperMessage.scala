package com.vilenet.coders.commands

import com.vilenet.Constants._
import com.vilenet.channels.{UserError, User}

/**
  * Created by filip on 12/16/15.
  */
object WhisperMessage {

  def apply(fromUser: User, command: String): Command = {
    val (toUsername, message) = CommandDecoder.spanBySpace(command)

    if (toUsername.nonEmpty) {
      if (message.nonEmpty) {
        WhisperMessage(fromUser, toUsername, message)
      } else {
        UserError(NO_MESSAGE_INPUT)
      }
    } else {
      UserError(NO_WHISPER_USER_INPUT)
    }
  }
}

case class WhisperMessage(override val fromUser: User, toUsername: String, override val message: String) extends UserCommand with MessageCommand
