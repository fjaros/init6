package com.init6.coders.commands

import com.init6.Constants._
import com.init6.channels.{UserError, User}

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
