package com.vilenet.coders.commands

import akka.actor.ActorRef
import com.vilenet.channels.User

/**
  * Created by filip on 12/16/15.
  */
case class WhoCommand(fromUser: User, override val message: String) extends MessageCommand

case class WhoCommandToChannel(actor: ActorRef, fromUser: User) extends Command
