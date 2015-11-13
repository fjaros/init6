package com.vilenet.channels

import akka.actor.ActorRef

/**
  * Created by filip on 11/9/15.
  */
class PublicChannelActor(name: String, limit: Int = 200) extends ChannelActor(name, limit) {

  override def add(actor: ActorRef, user: User) = {
    if (users.size >= limit) {
      actor ! UserError("Channel is full.")
    } else if (bannedUsers(user.name)) {
      actor ! UserError("You are banned from that channel.")
    } else {
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
  }

  override def checkDesignatees(forActor: ActorRef, actorUser: User) = {
    // no-op
  }
}

