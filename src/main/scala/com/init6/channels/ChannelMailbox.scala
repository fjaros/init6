package com.init6.channels

import java.util.Comparator

import akka.actor.ActorSystem
import akka.dispatch.{Envelope, PriorityGenerator, UnboundedStablePriorityMailbox}
import com.init6.coders.commands.{ChannelsCommand, ChatCommand, EmoteCommand, WhoCommand}
import com.typesafe.config.Config

/**
  * Created by filip on 11/7/15.
  */
class ChannelMailbox(settings: ActorSystem.Settings, config: Config)
  extends UnboundedStablePriorityMailbox(
    PriorityGenerator {
      case ChatCommand | EmoteCommand => 1
      case _ => 2
    }
  )

class ChannelsMailbox(settings: ActorSystem.Settings, config: Config)
  extends UnboundedStablePriorityMailbox(
    ChannelsPriorityGenerator {
      case ChannelsCommand | WhoCommand => 1
      case _: UserSwitchedChat => 2
      case _ => 4
    }
  )

object ChannelsPriorityGenerator {
  def apply(priorityFunction: Any => Int): ChannelsPriorityGenerator =
    (message: Any) => priorityFunction(message)
}

trait ChannelsPriorityGenerator extends Comparator[Envelope] {
  def gen(message: Any): Int

  override def compare(o1: Envelope, o2: Envelope) = {
    if (o1.message.isInstanceOf[UserSwitchedChat] && o2.message.isInstanceOf[UserSwitchedChat]) {
      val m1 = o1.message.asInstanceOf[UserSwitchedChat]
      val m2 = o2.message.asInstanceOf[UserSwitchedChat]

      if (m1.connectionTimestamp > m2.connectionTimestamp) {
        3
      } else {
        2
      }
    } else {
      gen(o1.message) - gen(o2.message)
    }
  }
}
