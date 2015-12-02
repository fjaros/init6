package com.vilenet.channels

import akka.actor.{Terminated, ActorRef}
import com.vilenet.channels.utils.{RemoteChannelsMultiMap, RemoteEvent}
import com.vilenet.coders.{EmoteMessage, ChatMessage}

import scala.collection.mutable

/**
  * Created by filip on 11/14/15.
  */
trait RemoteChannelActor extends ChannelActor {

  // Map of Actor -> Set[User], actor key is remote server's copy of this channel.
  var remoteUsers = RemoteChannelsMultiMap()

  override def receiveEvent: Receive = {
    case ChannelCreated(remoteChannelActor, _) =>
      println(s"ChannelCreated $remoteChannelActor")
      context.watch(remoteChannelActor)
      remoteUsers += remoteChannelActor
      remoteChannelActor ! RemoteEvent(ChannelUsersLoad(self, users, localUsers))

    case ChannelUsersRequest(remoteChannelsActor) =>
      println(s"ChannelUsersRequest $remoteChannelsActor")
      remoteChannelsActor ! ChannelUsersResponse(name, users, localUsers)

    case RemoteEvent(event) =>
      println(s"Received Remote $event ${this.getClass.toString}")
      receiveRemoteEvent(event)

    case event: Terminated =>
      println(s"REMOTECHANNELACTOR TERMINATED ${event.actor}")
      remoteUsers.get(event.actor).fold(super.receiveEvent(event))(_.foreach(remoteRem))

    case event =>
      super.receiveEvent(event)
      //println(s"Sending $event to $remoteUsers")
      //remoteUsers ! event
  }

  def receiveRemoteEvent: Receive = {
    case ChannelUsersLoad(remoteChannelActor, allUsers, remoteUsersLoad) =>
      log.error(s"ChannelUsersLoad $remoteChannelActor $allUsers $remoteUsersLoad")
      onChannelUsersLoad(remoteChannelActor, allUsers, remoteUsersLoad)
      remoteUsersLoad.foreach(context.watch)
      remoteUsers += remoteChannelActor -> remoteUsersLoad

    case AddUser(actor, user) => remoteAdd(actor, user)
    case RemUser(actor) => remoteRem(actor)

    case ChatMessage(user, message) =>
      println("Remote chat msg")
      localUsers ! UserTalked(user, message)
    case EmoteMessage(user, message) =>
      localUsers ! UserEmote(user, message)
    case event =>
  }

  def onChannelUsersLoad(remoteChannelActor: ActorRef, allUsers: mutable.Map[ActorRef, User], remoteUsersLoad: mutable.Set[ActorRef]) = {
    allUsers
      .filterNot(tuple => users.contains(tuple._1))
      .foreach(tuple => {
        log.error(s"Adding User From Load $tuple")
        users += tuple
        localUsers ! UserIn(tuple._2)
      })
  }

  override def add(actor: ActorRef, user: User): User = {
    val finalUser = super.add(actor, user)
    remoteUsers ! AddUser(actor, finalUser)
    finalUser
  }

  override def rem(actor: ActorRef): Option[User] = {
    val finalUserOpt = super.rem(actor)
    println(s"rem  $remoteUsers")
    remoteUsers ! RemUser(actor)
    finalUserOpt
  }

  def remoteAdd(actor: ActorRef, user: User): Unit = {
    println(s"REMOTEADD $name ${user.name}")
    context.watch(actor)
    users += actor -> user
  }

  def remoteRem(actor: ActorRef): Option[User] = {
    val userOpt = users.get(actor)
    userOpt.fold()(_ => {
      context.unwatch(actor)
      users -= actor
    })

    if (users.isEmpty) {
      channelsActor ! ChatEmptied(name)
    }
    userOpt
  }
}
