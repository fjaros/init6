package com.vilenet.channels

import akka.actor.ActorRef

/**
  * Created by filip on 11/12/15.
  */
object PrivateChannelActor {
  def apply(name: String) = new PrivateChannelActor(name)
}

sealed class PrivateChannelActor(channelName: String)
  extends ChattableChannelActor
  with BannableChannelActor
  with OperableChannelActor
  with FullableChannelActor {

  override val name = channelName

  override def add(actor: ActorRef, user: User): User = {
    super.add(actor, Flags.deOp(user))
  }
}
