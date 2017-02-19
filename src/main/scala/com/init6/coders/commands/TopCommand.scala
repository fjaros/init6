package com.init6.coders.commands

import com.init6.Config
import com.init6.channels.UserError

/**
  * Created by filip on 12/16/15.
  */
object TopCommand {

  def apply(message: String): Command = {
    if (message.nonEmpty) {
      val (first, second) = CommandDecoder.spanBySpace(message)
      if (second.nonEmpty) {
        parseWhich(second).fold[Command](
          UserError()
        )(
          TopCommand(parseServerIp(first).getOrElse(Config().Server.host), _)
        )
      } else {
        parseWhich(first).fold[Command]({
          parseServerIp(first).fold[Command](
            UserError()
          )(
            TopCommand(_, "all")
          )
        })(
          TopCommand(Config().Server.host, _)
        )
      }
    } else {
      TopCommand(Config().Server.host, "all")
    }
  }

  def parseServerIp(serverIp: String): Option[String] = {
    val lowered = serverIp.toLowerCase
    val nodes = Config().Server.allNodes
    lowered match {
      case "dal" | "dallas" => nodes.find(_.contains("dal"))
      case "sea" | "seattle" => nodes.find(_.contains("sea"))
      case "chi" | "chicago" => nodes.find(_.contains("chi"))
      case _ => None
    }
  }

  def parseWhich(which: String): Option[String] = {
    val lowered = which.toLowerCase
    lowered match {
      case "chat" | "binary" | "all" => Some(lowered)
      case "" => Some("all")
      case _ => None
    }
  }
}

case class TopCommand(serverIp: String, which: String) extends Command
