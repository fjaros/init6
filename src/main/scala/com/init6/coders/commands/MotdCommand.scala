package com.init6.coders.commands

import com.init6.Config
import com.init6.channels.UserInfoArray

/**
  * Created by filip on 12/16/15.
  */
object MotdCommand {

  def apply() = UserInfoArray(Config().motd)
}
