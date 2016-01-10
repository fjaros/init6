package com.vilenet.channels

import akka.actor.ActorRef
import com.vilenet.Constants._

/**
  * Created by filip on 11/25/15.
  */
object PublicChannelActor {
  def apply(name: String, remoteActor: Option[ActorRef]) = new PublicChannelActor(name, remoteActor)
}

sealed class PublicChannelActor(
                                  override val name: String,
                                  val remoteActor: Option[ActorRef]
                               )
  extends ChattableChannelActor
  with BannableChannelActor
  with NonOperableChannelActor
  with FullableChannelActor {

  remoteActor.foreach(remoteUsers += _)

  override def add(actor: ActorRef, user: User): User = {
    users.getOrElse(actor, {
      val addedUser = super.add(actor, Flags.deOp(user))
      actor ! UserInfo(PUBLIC_CHANNEL)
      addedUser
    })
  }
}
