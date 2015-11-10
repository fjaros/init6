package com.vilenet.channels

import akka.actor.{ActorRef, Props}

import scala.collection.mutable

/**
  * Created by filip on 11/9/15.
  */
class VoidedChannelActor(name: String) extends ChannelActor(name) {

  override def onChannelUsersLoad(remoteChannelActor: ActorRef, allUsers: mutable.Map[ActorRef, User], remoteUsersLoad: mutable.Set[ActorRef]) = {
    allUsers
      .filterNot(tuple => users.contains(tuple._1))
      .foreach(tuple => {
        log.error(s"Adding User From Load $tuple")
        users += tuple
      })
  }

  override def onChatMessage(user: User, message: String) = {
    // no-op
  }

  override def onRemoteChatMessage(user: User, message: String) = {
    // no-op
  }

  override def onEmoteMessage(user: User, message: String) = {
    // Send back only to sender
    sender() ! UserEmote(user, message)
  }

  override def add(actor: ActorRef, user: User) = {
    val updatedUser = user.copy(
      flags = user.flags & ~0x02,
      channel = name
    )

    users += actor -> updatedUser
    localUsers += actor
    context.watch(actor)

    actor ! UserChannel(updatedUser, name, self)
    actor ! UserInfo("This channel does not have chat privileges.")

    updatedUser
  }

  override def remoteAdd(actor: ActorRef, user: User) = {
    if (!users.contains(actor)) {
      users += actor -> user
      remoteUsers.get(sender()).fold(log.error(s"Remote user added but no remote channel actor found ${sender()}"))(_ += actor)
    }
  }

  override def rem(actor: ActorRef) = {
    users.get(actor).fold()(user => {
      context.unwatch(actor)
      users -= actor
      localUsers -= actor

      if (users.isEmpty) {
        channelsActor ! ChatEmptied(name)
      }
    })
  }

  override def remoteRem(actor: ActorRef) = {
    if (users.contains(actor)) {
      users -= actor
      remoteUsers.get(sender()).fold(log.error(s"Remote user removed but no remote channel actor found ${sender()}"))(_ -= actor)

      if (users.isEmpty) {
        channelsActor ! ChatEmptied(name)
      }
    }
  }
}
