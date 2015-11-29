package com.vilenet.channels

import com.vilenet.coders.EmoteMessage

/**
  * Created by filip on 11/24/15.
  */
object VoidedChannelActor {
  def apply(name: String) = new VoidedChannelActor(name)
}

sealed class VoidedChannelActor(channelName: String)
  extends ChannelActor {

  override val name = channelName

  override def receiveEvent = ({
    case EmoteMessage(_, message) => sender() ! UserEmote(users(sender()), message)
  }: Receive)
    .orElse(super.receiveEvent)
}
