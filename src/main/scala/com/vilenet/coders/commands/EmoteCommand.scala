package com.vilenet.coders.commands

import com.vilenet.channels.User

/**
  * Created by filip on 12/16/15.
  */
case class EmoteCommand(fromUser: User, message: String) extends MessageCommand with ChannelCommand
