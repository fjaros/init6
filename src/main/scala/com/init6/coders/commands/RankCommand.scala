package com.init6.coders.commands

import com.init6.Config
import com.init6.channels.{User, UserError}
import com.init6.users.GetRankingCommand

/**
  * Created by filip on 4/18/17.
  */
object RankCommand {

  def apply(user: User, message: String): Command = {
    if (message.nonEmpty) {
      val splt = message.split(" ")
      parseServerIp(splt.last).fold(
        GetRankingCommand(Config().Server.host, message)
      )(
        GetRankingCommand(_, splt.dropRight(1).mkString(" "))
      )
    } else {
      GetRankingCommand(Config().Server.host, user.inChannel)
    }
  }

  def parseServerIp(serverIp: String): Option[String] = {
    val lowered = serverIp.toLowerCase
    val nodes = Config().Server.allNodes
    lowered match {
      case "dal" | "dallas" => nodes.find(_.contains("dal"))
      case "sea" | "seattle" => nodes.find(_.contains("sea"))
      case "chi" | "chicago" => nodes.find(_.contains("chi"))
      case "chat" => nodes.find(_.contains("chat"))
      case _ => None
    }
  }
}
