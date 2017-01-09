package com.vilenet.channels

import com.vilenet.Constants._
import com.vilenet.coders.commands.{ChannelInfo, ChannelsCommand, EmoteCommand, WhoCommandToChannel}
import com.vilenet.users.{GetUsers, UpdatePing}

/**
  * Created by filip on 11/24/15.
  */
object VoidedChannelActor {
  def apply(name: String) = new VoidedChannelActor(name)
}

sealed class VoidedChannelActor(override val name: String)
  extends NonOperableChannelActor {

  override def receiveEvent = ({
    case WhoCommandToChannel(_, _) | UpdatePing(_) => // No-op
    case GetUsers => sender() ! UserInfo(NO_CHAT_PRIVILEGES)
    case ChannelsCommand => sender() ! ChannelInfo(name, 0, topicExchange.topic)
    case EmoteCommand(_, message) =>
      if (isLocal()) {
        sender() ! UserEmote(users(sender()), message)
      }
  }: Receive)
    .orElse(super.receiveEvent)
}
