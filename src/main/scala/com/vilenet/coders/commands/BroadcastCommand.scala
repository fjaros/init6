package com.vilenet.coders.commands

import com.vilenet.channels.{Flags, User, UserError}
import com.vilenet.servers.Remotable

/**
  * Created by filip on 12/19/15.
  */
object BroadcastCommand {

  def apply(fromUser: User, message: String): Command = {
    if (Flags.isAdmin(fromUser)) {
      BroadcastCommand(message)
    } else {
      UserError()
    }
  }
}

case class BroadcastCommand(message: String) extends Command with Remotable
