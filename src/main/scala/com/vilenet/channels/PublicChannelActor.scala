package com.vilenet.channels

import akka.actor.ActorRef

/**
  * Created by filip on 11/9/15.
  */
class PublicChannelActor(name: String) extends ChannelActor(name) {

  override def add(actor: ActorRef, user: User) = {
    val updatedUser = user.copy(
      flags = user.flags & ~0x02,
      channel = name
    )

    val userJoined = UserJoined(updatedUser)
    localUsers
      .foreach(_ ! userJoined)

    users += actor -> updatedUser
    localUsers += actor
    context.watch(actor)

    actor ! UserChannel(updatedUser, name, self)
    actor ! UserInfo("This is a Public Chat channel. No Ops given.")

    users
      .values
      .foreach(actor ! UserIn(_))

    updatedUser
  }

  override def checkDesignatees(forActor: ActorRef, actorUser: User) = {
    // no-op
  }
}

