package com.init6.coders.commands

import com.init6.channels.User

/**
  * Created by filip on 12/16/15.
  */
case class DesignateCommand(override val fromUser: User, override val toUsername: String) extends UserCommand with OperableCommand with UserToChannelCommand
