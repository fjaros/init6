package com.vilenet.coders.commands

import com.vilenet.Constants._
import com.vilenet.channels.{UserInfo, User}

/**
  * Created by filip on 12/16/15.
  */
object PlaceCommand {

  def apply(user: User): Command = {
    UserInfo(PLACED(user.place))
  }
}
