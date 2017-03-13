package com.init6.coders.commands

/**
  * Created by filip on 12/16/15.
  */
case object ChannelsCommand extends Command

//case class ChannelsCommand(actor: ActorRef) extends Command

case class ChannelInfo(name: String, size: Int, topic: String, creationTime: Long) extends Command
