package com.vilenet.channels

import akka.actor.{ActorRef, Props}
import com.vilenet.ViLeNetActor
import com.vilenet.servers.RemoteEvent

import scala.collection.mutable

/**
 * Created by filip on 9/20/15.
 */
object ChannelActor {
  def apply(name: String) = Props(new ChannelActor(name))
}

case class User(
                 // Static variables
                 name: String,
                 flags: Long = 0,
                 ping: Long = 0,
                 client: String = "CHAT",

                 // Changeable
                 channel: String = "Chat")

case class ServerTerminated(columbus: ActorRef)
case class ChannelUsersRequest(remoteChannelsActor: ActorRef)
case class ChannelUsersResponse(name: String, allUsers: mutable.Map[ActorRef, User], remoteUsers: mutable.Set[ActorRef])
case class ChannelUsersLoad(remoteChannelActor: ActorRef, allUsers: mutable.Map[ActorRef, User], remoteUsers: mutable.Set[ActorRef])

case class AddUser(actor: ActorRef, user: User)
case class AddLocalUser(actor: ActorRef, user: User)
case class RemUser(actor: ActorRef)

object LocalUsersSet {
  def apply() = new LocalUsersSet()
}

class LocalUsersSet extends mutable.HashSet[ActorRef] {
  def !(message: Any): Unit = foreach(_ ! message)
}

object RemoteChannelsMultiMap {
  def apply() = new RemoteChannelsMultiMap()
}

class RemoteChannelsMultiMap extends mutable.HashMap[ActorRef, mutable.Set[ActorRef]] with mutable.MultiMap[ActorRef, ActorRef] {

  private val columbusToChannelMap = mutable.Map[ActorRef, ActorRef]()

  def +=(columbus: ActorRef, kv: (ActorRef, ActorRef)): this.type = {
    columbusToChannelMap += columbus -> kv._1
    +=(kv)
  }

  def -=(columbus: ActorRef, key: ActorRef): this.type = {
    columbusToChannelMap -= columbus
    -=(key)
  }

  def +=(kv: (ActorRef, ActorRef)): this.type = addBinding(kv._1, kv._2)
  def +=(key: ActorRef): this.type = +=(key -> mutable.Set[ActorRef]())
  def !(message: Any): Unit = keys.foreach(_ ! RemoteEvent(message))

  def getByColumbus(columbus: ActorRef): Option[mutable.Set[ActorRef]] = {
    columbusToChannelMap.get(columbus).fold[Option[mutable.Set[ActorRef]]](None)(get)
  }
}

class ChannelActor(name: String) extends ViLeNetActor {

  // Set of users in this channel on this server
  var localUsers = LocalUsersSet()

  // Map of Actor -> Set[User], actor key is remote server's copy of this channel.
  var remoteUsers = RemoteChannelsMultiMap()

  // Linked Map of actor -> user. Actor can be local or remote.
  var users = mutable.LinkedHashMap[ActorRef, User]()

  /*
    USE CASES:
      Add/Rem User:
        - localUsers foreach send UserJoin/UserLeave
        - remoteUsers foreach send RemoteAddUser/RemoteRemUser

      Talk/Emote:
        - localUsers foreach send talk/emote
        - remoteUsers foreach send talk/emote

      Server split:
        - users foreach
          if (user.serverColumbus == sender()) {
            Rem(user)
          }
   */


  override def receive: Receive = {
    case ChannelUsersRequest(remoteChannelsActor) =>
      remoteChannelsActor ! ChannelUsersResponse(name, users, localUsers)

    case ServerTerminated(columbus) =>
      remoteUsers.getByColumbus(columbus)
        .fold(log.info(s"Remote server terminated but not found as a users key $columbus"))(_.foreach(rem))

    case AddLocalUser(actor, user) =>
      add(actor, user)
      localUsers += actor

    case RemoteEvent(event) =>
      handleRemote(event)

    case event =>
      handleLocal(event)
      remoteUsers ! event
  }

  def handleLocal: Receive = {
    case AddUser(actor, user) =>
      add(actor, user)
      localUsers += actor

    case RemUser(actor) =>
      rem(actor)
      localUsers -= actor

  }

  def handleRemote: Receive = {
    case ChannelUsersLoad(remoteChannelActor, allUsers, remoteUsersLoad) =>
      remoteUsers += remoteChannelActor -> remoteUsersLoad
      allUsers.foreach(tuple => {
        val actor = tuple._1
        val user = tuple._2
        users += actor -> user
        remoteUsers.get(sender()).fold(log.error(s"Remote user added but no remote channel actor found ${sender()}"))(_ += actor)
        val userJoined = UserJoined(user)
        localUsers
          .foreach(_ ! userJoined)
      })

    case AddUser(actor, user) =>
      users += actor -> user
      remoteUsers.get(sender()).fold(log.error(s"Remote user added but no remote channel actor found ${sender()}"))(_ += actor)
      val userJoined = UserJoined(user)
      localUsers
        .foreach(_ ! userJoined)

    case RemUser(actor) =>
      val user = users(actor)
      users -= actor
      remoteUsers.get(sender()).fold(log.error(s"Remote user added but no remote channel actor found ${sender()}"))(_ -= actor)
      val userJoined = UserLeft(user)
      localUsers
        .foreach(_ ! userJoined)

  }

  def add(actor: ActorRef, user: User) = {
    val userJoined = UserJoined(user)
    localUsers
      .foreach(_ ! userJoined)

    users += actor -> user
    localUsers += actor

    localUsers
      .map(users(_))
      .map(UserIn)
      .foreach(actor ! _)
  }

  def rem(actor: ActorRef) = {
    val userLeft = UserLeft(users(actor))

    users -= actor
    localUsers -= actor

    localUsers
      .foreach(_ ! userLeft)
  }
}
