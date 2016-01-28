package com.vilenet.channels

import akka.actor.ActorRef
import com.vilenet.Constants._
import com.vilenet.coders.commands.{ChannelInfo, ChannelsCommand, EmoteCommand}

/**
  * Created by filip on 11/24/15.
  */
object VoidedChannelActor {
  def apply(name: String) = new VoidedChannelActor(name)
}

sealed class VoidedChannelActor(channelName: String)
  extends NonOperableChannelActor {

  override val name = channelName

  override def receiveEvent = ({
    case ChannelCreated => // No-op
    case ChannelsCommand => sender() ! ChannelInfo(name, 0)
    case EmoteCommand(_, message) => sender() ! UserEmote(users(sender()), message)
  }: Receive)
    .orElse(super.receiveEvent)

  override def add(actor: ActorRef, user: User): User = {
    users.getOrElse(actor, {
      val addedUser = super.add(actor, user)
      actor ! UserInfo(NO_CHAT_PRIVILEGES)
      addedUser
    })
  }

  override def whoCommand(actor: ActorRef, user: User) = {
    // No-op
  }
}
