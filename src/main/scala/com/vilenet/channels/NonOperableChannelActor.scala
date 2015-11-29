package com.vilenet.channels

import akka.actor.ActorRef

/**
  * Created by filip on 11/25/15.
  */
trait NonOperableChannelActor extends ChannelActor {

  override def add(actor: ActorRef, user: User): User = {
    val newUser = Flags.deOp(user)

    super.add(actor, newUser)
  }
}
