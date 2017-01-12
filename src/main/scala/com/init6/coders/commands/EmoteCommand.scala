package com.init6.coders.commands

import com.init6.channels.User
import com.init6.servers.Remotable

/**
  * Created by filip on 12/16/15.
  */
case class EmoteCommand(fromUser: User, message: String) extends MessageCommand with ChannelCommand with Remotable
