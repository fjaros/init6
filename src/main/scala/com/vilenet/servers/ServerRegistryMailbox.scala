package com.vilenet.servers

import akka.actor.{ActorIdentity, ActorSystem, Identify}
import akka.dispatch.{PriorityGenerator, UnboundedStablePriorityMailbox}
import com.typesafe.config.Config

/**
  * Created by filip on 1/6/17.
  */
class ServerRegistryMailbox(settings: ActorSystem.Settings, config: Config)
  extends UnboundedStablePriorityMailbox(
    PriorityGenerator {
      case Identify | ActorIdentity => 1
      case _ => 2
    }
  )
