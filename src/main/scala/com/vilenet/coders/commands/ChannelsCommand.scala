package com.vilenet.coders.commands

import akka.actor.ActorRef

/**
  * Created by filip on 12/16/15.
  */
case object ChannelsCommand extends Command

case class ChannelsCommand(actor: ActorRef) extends Command
