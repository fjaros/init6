package com.init6.coders.commands

import com.init6.channels.User
import com.init6.servers.Remotable

/**
  * Created by filip on 12/10/15.
  */
trait Command

trait MessageCommand extends Command {
  val message: String
}

trait ChannelCommand extends Command {
  val fromUser: User
}
trait ChannelBroadcastable extends ChannelCommand with Remotable {
  val message: String
}

trait UserCommand extends Command {
  val fromUser: User
  val toUsername: String
}
trait UserToChannelCommand extends Command {
  val toUsername: String
}
trait OperableCommand extends Command
trait ReturnableCommand extends Command
