package com.vilenet.channels

import akka.actor.ActorRef
import com.vilenet.Constants._

/**
  * Created by filip on 11/25/15.
  */
object AdminChannelActor {
  def apply(name: String, remoteActor: Option[ActorRef]) = new AdminChannelActor(name, remoteActor)
}

sealed class AdminChannelActor(
                                override val name: String,
                                val remoteActor: Option[ActorRef]
                              )
  extends ChattableChannelActor
  with NonOperableChannelActor
  with RemoteChannelActor {

  remoteActor.foreach(remoteUsers += _)

  override def add(actor: ActorRef, user: User) = {
    if (Flags.isAdmin(user)) {
      super.add(actor, user)
    } else {
      actor ! UserError(CHANNEL_RESTRICTED)
      user
    }
  }

  override def whoCommand(actor: ActorRef, user: User) = {
    if (Flags.isAdmin(user)) {
      super.whoCommand(actor, user)
    } else {
      actor ! UserError(NOT_ALLOWED_TO_VIEW)
    }
  }
}
