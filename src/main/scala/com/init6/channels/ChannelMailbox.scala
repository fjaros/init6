package com.init6.channels

import akka.actor.ActorSystem
import akka.dispatch.{PriorityGenerator, UnboundedStablePriorityMailbox}
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
    new PriorityGenerator {

      var minConTime: Option[Long] = None

      override def gen(message: Any) = {
        message match {
          case ChannelsCommand | WhoCommand => 1
          case UserSwitchedChat(_, _, _, connectionTimestamp) =>
            val msgOrder = minConTime
              .fold({
                minConTime = Some(connectionTimestamp)
                2
              })(time => {
                if (connectionTimestamp < time) {
                  minConTime = Some(connectionTimestamp)
                  2
                } else {
                  3
                }
              })
            msgOrder
          case _ => 3
        }
      }
    }
  )
