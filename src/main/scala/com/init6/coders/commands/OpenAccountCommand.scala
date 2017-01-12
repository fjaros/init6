package com.init6.coders.commands

import com.init6.channels.{Flags, User, UserError}

/**
  * Created by fjaros on 12/28/16.
  */
object OpenAccountCommand {

  def apply(user: User, message: String): Command = {
    if (Flags.isAdmin(user)) {
      OpenAccountCommand(message)
    } else {
      UserError()
    }
  }
}

case class OpenAccountCommand(account: String) extends Command
