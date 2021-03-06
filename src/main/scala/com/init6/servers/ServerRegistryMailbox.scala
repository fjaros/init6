package com.init6.servers

import akka.actor.{ActorIdentity, ActorSystem, Identify}
import akka.dispatch.{PriorityGenerator, UnboundedPriorityMailbox}
import com.typesafe.config.Config

/**
  * Created by filip on 1/6/17.
  */
class ServerRegistryMailbox(settings: ActorSystem.Settings, config: Config)
  extends UnboundedPriorityMailbox(
    PriorityGenerator {
      case Identify(_) | ActorIdentity(_, _) | IAmHere | AreYouThere => 1
      case _ => 2
    }
  )
