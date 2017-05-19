package com.init6.coders.commands

import com.init6.channels.{User, UserError}
import com.init6.rpg.{RpgDuel, RpgPlay, RpgStatus, RpgTravel}

/**
  * Created by filip on 5/14/17.
  */
object RpgCommand {

  def apply(user: User, message: String): Command = {
    val (command, rest) = CommandDecoder.spanBySpace(message)

    command.toLowerCase match {
      case "play" => RpgPlay(user.id, user.name)
      case "status" | "" => RpgStatus(user.id)
      case "travel" => RpgTravel(user.id, rest)
      case "duel" => RpgDuel(user.id, rest)
      case "help" => UserError()
      case _ => UserError()
    }
  }
}
