package com.init6.coders.commands

import com.init6.channels.User
import com.init6.servers.Remotable

/**
  * Created by fjaros on 12/27/16.
  */
case class TopicCommand(override val fromUser: User, message: String) extends ChannelCommand with OperableCommand with Remotable
