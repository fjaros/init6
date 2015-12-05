package com.vilenet.channels

import akka.actor.{PoisonPill, Terminated, Props, ActorRef}
import com.vilenet.Constants._
import com.vilenet.ViLeNetActor
import com.vilenet.channels.utils.{RemoteEvent, LocalUsersSet}
import com.vilenet.coders.ChannelsCommand
import com.vilenet.users.UserToChannelCommandAck

import scala.annotation.switch
import scala.collection.mutable

/**
  * Created by filip on 11/12/15.
  */
object ChannelActor {
  // Channel Factory
  def apply(name: String) = Props({
    (name.toLowerCase: @switch) match {
      case "backstage" => AdminChannelActor("Backstage")
      case "the void" => VoidedChannelActor("The Void")
      case "vile" => PublicChannelActor("ViLe")
      case _ => PrivateChannelActor(name)
    }
  })
}

case class User(
                 // Static variables
                 name: String,
                 flags: Long = 0,
                 ping: Long = 0,
                 client: String = "CHAT",

                 // Changeable
                 channel: String = "Chat"
               )

case class ServerTerminated(columbus: ActorRef)
case class ChannelUsersRequest(remoteChannelsActor: ActorRef)
case class ChannelUsersResponse(name: String, allUsers: mutable.Map[ActorRef, User], remoteUsers: mutable.Set[ActorRef])
case class ChannelUsersLoad(remoteChannelActor: ActorRef, allUsers: mutable.Map[ActorRef, User], remoteUsers: mutable.Set[ActorRef])

case class AddUser(actor: ActorRef, user: User)
case class AddLocalUser(actor: ActorRef, user: User)
case class RemUser(actor: ActorRef)

trait ChannelActor extends ViLeNetActor {

  val name: String

  val limit = 200

  // Set of users in this channel on this server
  var localUsers = LocalUsersSet()

  // Linked Map of actor -> user. Actor can be local or remote.
  var users = mutable.LinkedHashMap[ActorRef, User]()

  // Final. Should not be overriden in subclasses. Use receiveEvent to avoid calling super to an abstract declaration
  final override def receive: Receive = {
    case event => receiveEvent(event)
  }

  def add(actor: ActorRef, user: User): User = {
    context.watch(actor)

    sender() ! UserAdded(actor, user.channel)

    val newUser = user.copy(channel = name)

    users += actor -> newUser
    println(s"### ACTOR ${newUser.name} $name")
    actor ! UserChannel(newUser, name, self)
    newUser
  }

  def rem(actor: ActorRef): Option[User] = {
    context.unwatch(actor)

    val userOpt = users.get(actor)
    users.get(actor).fold()(_ => users -= actor)
    if (users.isEmpty) {
      channelsActor ! ChatEmptied(name)
      self ! PoisonPill
    }
    userOpt
  }

  def receiveEvent: Receive = {
    case AddUser(actor, user) => add(actor, user)
    case RemUser(actor) => rem(actor)
    case Terminated(actor) => rem(actor)
    case ChannelsCommand(actor) =>
      actor ! UserInfo(CHANNEL_INFO(name, users.size))
    case event =>
  }
}
