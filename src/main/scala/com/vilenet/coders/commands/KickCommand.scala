package com.vilenet.coders.commands

/**
  * Created by filip on 12/16/15.
  */
object KickCommand {

  def apply(command: String): KickCommand = {
    val (account, message) = CommandDecoder.spanBySpace(command)
    KickCommand(account, message)
  }
}

case class KickCommand(override val toUsername: String, message: String) extends UserToChannelCommand with OperableCommand
