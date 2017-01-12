package com.init6.coders.commands

import com.init6.channels.User

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
trait UserCommand extends Command {
  val fromUser: User
  val toUsername: String
}
trait UserToChannelCommand extends Command {
  val toUsername: String
}
trait OperableCommand extends Command
trait ReturnableCommand extends Command

case class BlizzMe(fromUser: User) extends ChannelCommand
