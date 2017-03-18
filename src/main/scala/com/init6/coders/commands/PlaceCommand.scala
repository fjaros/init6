package com.init6.coders.commands

import com.init6.Config
import com.init6.channels.User

/**
  * Created by filip on 12/16/15.
  */
object PlaceCommand {

  def apply(user: User, message: String): Command = {
    if (message.nonEmpty) {
      parseServerIp(message).fold[Command](PlaceOfUserCommand(user, message))(PlaceOnServerCommand)
    } else {
      PlaceOfSelfCommand
    }
  }

  def parseServerIp(serverIp: String): Option[String] = {
    val lowered = serverIp.toLowerCase
    val nodes = Config().Server.allNodes
    lowered match {
      case "d" => nodes.find(_.contains("dal"))
      case "s" => nodes.find(_.contains("sea"))
      case "c" => nodes.find(_.contains("chi"))
      case _ => None
    }
  }

}

case object PlaceOfSelfCommand extends Command
case class PlaceOfUserCommand(override val fromUser: User, override val toUsername: String) extends UserCommand
case class PlaceOnServerCommand(serverIp: String) extends Command
