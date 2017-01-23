package com.init6.coders.commands

import com.init6.Config
import com.init6.channels.ServerTopicArray

/**
  * Created by filip on 12/16/15.
  */
object MotdCommand {

  def apply() = ServerTopicArray(Config().motd)
}
