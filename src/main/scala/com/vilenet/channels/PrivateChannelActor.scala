package com.vilenet.channels

import akka.actor.ActorRef

/**
  * Created by filip on 11/12/15.
  */
object PrivateChannelActor {
  def apply(name: String) = new PrivateChannelActor(name)
}

sealed class PrivateChannelActor(override val name: String)
  extends ChattableChannelActor
  with BannableChannelActor
  with OperableChannelActor
  with FullableChannelActor {

  override def add(actor: ActorRef, user: User): User = {
    super.add(actor, Flags.deOp(user))
  }
}
