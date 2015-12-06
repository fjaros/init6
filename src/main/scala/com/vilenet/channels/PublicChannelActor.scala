package com.vilenet.channels

import akka.actor.ActorRef
import com.vilenet.Constants._

/**
  * Created by filip on 11/25/15.
  */
object PublicChannelActor {
  def apply(name: String) = new PublicChannelActor(name)
}

sealed class PublicChannelActor(channelName: String)
  extends ChattableChannelActor
  with BannableChannelActor
  with NonOperableChannelActor
  with FullableChannelActor {

  override val name = channelName

  override def add(actor: ActorRef, user: User): User = {
    val addedUser = super.add(actor, user)
    actor ! UserInfo(PUBLIC_CHANNEL)
    addedUser
  }
}
