package com.init6.coders.commands

import com.init6.Config
import com.init6.Constants._
import com.init6.channels.{User, UserInfo}

/**
  * Created by filip on 12/16/15.
  */
object PlaceCommand {

  def apply(user: User, message: String): Command = {
    if (message.nonEmpty) {
      PlaceOfUserCommand(user, message)
    } else {
      UserInfo(PLACED(user.place, Config().Server.host))
    }
  }
}

case class PlaceOfUserCommand(override val fromUser: User, override val toUsername: String) extends UserCommand
