package com.vilenet.channels

import akka.actor.ActorSystem
import akka.dispatch.{UnboundedStablePriorityMailbox, PriorityGenerator}
import com.typesafe.config.Config
import com.vilenet.coders.commands.{EmoteCommand, ChatCommand}
import com.vilenet.servers.RemoteEvent

/**
  * Created by filip on 11/7/15.
  */
class ChannelMailbox(settings: ActorSystem.Settings, config: Config)
  extends UnboundedStablePriorityMailbox(
    PriorityGenerator {
      case ChatCommand |
           EmoteCommand |
           RemoteEvent(ChatCommand) |
           RemoteEvent(EmoteCommand) => 1
      case _ => 2
    }
  )
