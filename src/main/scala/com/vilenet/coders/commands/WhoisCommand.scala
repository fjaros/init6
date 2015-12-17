package com.vilenet.coders.commands

import com.vilenet.channels.User

/**
  * Created by filip on 12/16/15.
  */
case class WhoisCommand(override val fromUser: User, override val toUsername: String) extends UserCommand
