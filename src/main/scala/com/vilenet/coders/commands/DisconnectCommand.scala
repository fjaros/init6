package com.vilenet.coders.commands

import com.vilenet.channels.{Flags, User, UserError}

/**
  * Created by fjaros on 12/25/16.
  */
object DisconnectCommand {

  def apply(fromUser: User, message: String): Command = {
    if (Flags.isAdmin(fromUser)) {
      DisconnectCommand(message)
    } else {
      UserError()
    }
  }
}

case class DisconnectCommand(user: String) extends Command
