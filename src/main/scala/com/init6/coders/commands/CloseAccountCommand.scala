package com.init6.coders.commands

import com.init6.channels.{Flags, User, UserError}

/**
  * Created by fjaros on 12/28/16.
  */
object CloseAccountCommand {

  def apply(user: User, message: String): Command = {
    if (Flags.isAdmin(user)) {
      val (account, reason) = CommandDecoder.spanBySpace(message)
      CloseAccountCommand(account, reason)
    } else {
      UserError()
    }
  }
}

case class CloseAccountCommand(account: String, reason: String) extends Command
