package com.vilenet.coders.commands

/**
  * Created by filip on 12/16/15.
  */
object BanCommand {

  def apply(command: String): BanCommand = {
    val (account, message) = CommandDecoder.spanBySpace(command)
    BanCommand(account, message)
  }
}

case class BanCommand(override val toUsername: String, message: String) extends UserToChannelCommand with OperableCommand
