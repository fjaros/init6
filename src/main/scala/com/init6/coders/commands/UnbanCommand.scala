package com.init6.coders.commands

/**
  * Created by filip on 12/16/15.
  */
case class UnbanCommand(override val toUsername: String) extends UserToChannelCommand with OperableCommand