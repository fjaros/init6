package com.vilenet.coders.commands

import com.vilenet.Constants._
import com.vilenet.channels.UserInfoArray

/**
  * Created by filip on 12/16/15.
  */
object MotdCommand {

  def apply() = UserInfoArray(MOTD)
}
