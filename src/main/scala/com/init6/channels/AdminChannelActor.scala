package com.init6.channels

import akka.actor.ActorRef
import com.init6.Constants._

/**
  * Created by filip on 11/25/15.
  */
object AdminChannelActor {
  def apply(name: String) = new AdminChannelActor(name)
}

sealed class AdminChannelActor(override val name: String)
  extends ChattableChannelActor
  with NonOperableChannelActor {

  override def add(actor: ActorRef, user: User) = {
    if (isRemote(actor) || Flags.isAdmin(user)) {
      super.add(actor, user)
    } else {
      sender() ! UserError(CHANNEL_RESTRICTED)
      user
    }
  }

  override def whoCommand(actor: ActorRef, user: User, opsOnly: Boolean) = {
    if (Flags.isAdmin(user)) {
      super.whoCommand(actor, user, opsOnly)
    } else {
      actor ! WhoCommandError(NOT_ALLOWED_TO_VIEW)
    }
  }
}
