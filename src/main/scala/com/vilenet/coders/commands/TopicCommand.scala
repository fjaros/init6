package com.vilenet.coders.commands

import com.vilenet.channels.User

/**
  * Created by fjaros on 12/27/16.
  */
case class TopicCommand(override val fromUser: User, message: String) extends ChannelCommand with OperableCommand
