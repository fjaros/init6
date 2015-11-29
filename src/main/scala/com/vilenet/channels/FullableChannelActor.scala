package com.vilenet.channels

import akka.actor.ActorRef
import com.vilenet.Constants._

/**
  * Created by filip on 11/24/15.
  */
trait FullableChannelActor extends ChannelActor {

  override def add(actor: ActorRef, user: User): User = {
    if (users.size >= limit) {
      actor ! UserError(CHANNEL_FULL)
      user
    } else {
      super.add(actor, user)
    }
  }
}
