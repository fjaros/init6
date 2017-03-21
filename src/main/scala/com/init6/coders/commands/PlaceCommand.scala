package com.init6.coders.commands

import com.init6.Config
import com.init6.channels.{Flags, User}

/**
  * Created by filip on 12/16/15.
  */
object PlaceCommand {

  def apply(user: User, message: String): Command = {
    if (message.nonEmpty) {
      if (Flags.isAdmin(user)) {
        parseServerIp(message).fold[Command](PlaceOfUserCommand(user, message))(PlaceOnServerCommand)
      } else {
        PlaceOfUserCommand(user, message)
      }
    } else {
      PlaceOfSelfCommand
    }
  }

  def parseServerIp(serverIp: String): Option[String] = {
    val lowered = serverIp.toLowerCase
    val nodes = Config().Server.allNodes
    lowered match {
      case "dal" => nodes.find(_.contains("dal"))
      case "sea" => nodes.find(_.contains("sea"))
      case "chi" => nodes.find(_.contains("chi"))
      case "chat" => nodes.find(_.contains("chat"))
      case _ => None
    }
  }

}

case object PlaceOfSelfCommand extends Command
case class PlaceOfUserCommand(override val fromUser: User, override val toUsername: String) extends UserCommand
case class PlaceOnServerCommand(serverIp: String) extends Command
