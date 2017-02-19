package com.init6.coders.commands

import com.init6.servers.Remotable

/**
  * Created by filip on 12/19/15.
  */
case class BroadcastCommand(message: String) extends Command with Remotable

case class BroadcastCommandToLocal(message: String) extends Command
