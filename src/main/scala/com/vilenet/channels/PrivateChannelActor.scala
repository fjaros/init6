package com.vilenet.channels

import akka.actor.ActorRef

/**
  * Created by filip on 11/12/15.
  */
object PrivateChannelActor {
  def apply(name: String, remoteActor: Option[ActorRef]) = new PrivateChannelActor(name, remoteActor)
}

sealed class PrivateChannelActor(
                                  override val name: String,
                                  val remoteActor: Option[ActorRef]
                                )
  extends ChattableChannelActor
  with BannableChannelActor
  with OperableChannelActor
  with FullableChannelActor {

  remoteActor.foreach(remoteUsers += _)

  override def add(actor: ActorRef, user: User): User = {
    users.getOrElse(actor, super.add(actor, Flags.deOp(user)))
  }
}
