package com.vilenet.channels

import akka.actor.ActorRef
import com.vilenet.Constants._

/**
  * Created by filip on 11/25/15.
  */
object AdminChannelActor {
  def apply(name: String) = new AdminChannelActor(name)
}

sealed class AdminChannelActor(channelName: String)
  extends ChattableChannelActor
  with NonOperableChannelActor
  with RemoteChannelActor {

  override val name = channelName

  override def add(actor: ActorRef, user: User) = {
    if (Flags.isAdmin(user)) {
      super.add(actor, user)
    } else {
      actor ! UserError(CHANNEL_RESTRICTED)
      user
    }
  }
}
