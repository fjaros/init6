package com.vilenet.coders.commands

import com.vilenet.Constants._
import com.vilenet.channels.{UserError, User}

/**
  * Created by filip on 12/16/15.
  */
object SquelchCommand {

  def apply(fromUser: User, toUsername: String): Command = {
    if (toUsername.nonEmpty) {
      if (fromUser.name.equalsIgnoreCase(toUsername)) {
        UserError(YOU_CANT_SQUELCH)
      } else {
        SquelchCommand(toUsername)
      }
    } else {
      UserError(USER_NOT_LOGGED_ON)
    }
  }
}

case class SquelchCommand(override val toUsername: String) extends UserToChannelCommand
