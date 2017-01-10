package com.vilenet.coders.commands

import com.vilenet.servers.Remotable

/**
  * Created by filip on 12/19/15.
  */
case class BroadcastCommand(message: String) extends Command with Remotable
