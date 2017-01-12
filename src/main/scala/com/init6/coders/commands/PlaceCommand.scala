package com.init6.coders.commands

import com.init6.Constants._
import com.init6.channels.{UserInfo, User}

/**
  * Created by filip on 12/16/15.
  */
object PlaceCommand {

  def apply(user: User): Command = {
    UserInfo(PLACED(user.place))
  }
}
