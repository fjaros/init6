package com.init6.channels

import akka.actor.ActorRef
import com.init6.Constants._

/**
  * Created by filip on 1/12/17.
  */
object LockedChannelActor {
  def apply(name: String, lockedMessage: String) = new LockedChannelActor(name, lockedMessage)
}

sealed class LockedChannelActor(override val name: String, lockedMessage: String)
  extends ChannelActor {

  override def add(actor: ActorRef, user: User) = {
    sender() ! UserError(lockedMessage)
    user
  }

  override def whoCommand(actor: ActorRef, user: User) = {
    actor ! WhoCommandError(NOT_ALLOWED_TO_VIEW)
  }
}
