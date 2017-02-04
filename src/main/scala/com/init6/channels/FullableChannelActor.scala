package com.init6.channels

import akka.actor.ActorRef
import com.init6.Constants._

/**
  * Created by filip on 11/24/15.
  */
trait FullableChannelActor extends ChannelActor {

  override def add(actor: ActorRef, user: User): User = {
    if (isRemote(actor) || limit > users.size || Flags.isAdmin(user)) {
      super.add(actor, user)
    } else {
      sender() ! UserError(CHANNEL_FULL)
      user
    }
  }
}
