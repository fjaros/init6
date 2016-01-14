package com.vilenet.channels

import akka.actor.{Terminated, Props, ActorRef}
import com.vilenet.Constants._
import com.vilenet.ViLeNetActor
import com.vilenet.channels.utils.LocalUsersSet
import com.vilenet.coders.commands.Command
import com.vilenet.coders.commands.{WhoCommandToChannel, ChannelsCommand}
import com.vilenet.users.{UserUpdated, UpdatePing}

import scala.annotation.switch
import scala.collection.mutable

/**
  * Created by filip on 11/12/15.
  */
object ChannelActor {
  // Channel Factory
  def apply(name: String, remoteChannelActor: Option[ActorRef]) = Props({
    (name.toLowerCase: @switch) match {
      case "backstage" => AdminChannelActor("Backstage", remoteChannelActor)
      case "the void" => VoidedChannelActor("The Void")
      case "vile" => PublicChannelActor("ViLe", remoteChannelActor)
      case _ => PrivateChannelActor(name, remoteChannelActor)
    }
  })
}

case class User(
                 // Static variables
                 name: String,
                 flags: Long = 0,
                 ping: Long = 0,
                 client: String = "CHAT",
                 place: Int = 0,

                 // Changeable
                 channel: String = ""
               ) extends Command

case class AddUser(actor: ActorRef, user: User) extends Command
case class RemUser(actor: ActorRef) extends Command
case object CheckSize extends Command
case class ChannelSize(size: Int) extends Command

trait ChannelActor extends ViLeNetActor {

  val name: String

  val limit = 200

  // Set of users in this channel on this server
  val localUsers = LocalUsersSet()

  // Linked Map of actor -> user. Actor can be local or remote.
  val users = mutable.LinkedHashMap[ActorRef, User]()

  // Final. Should not be overriden in subclasses. Use receiveEvent to avoid calling super to an abstract declaration
  final override def receive: Receive = {
    case event => receiveEvent(event)
  }

  def add(actor: ActorRef, user: User): User = {
    context.watch(actor)

    val newUser = user.copy(channel = name)

    users += actor -> newUser
    actor ! UserChannel(newUser, name, self)
    newUser
  }

  def rem(actor: ActorRef): Option[User] = {
    context.unwatch(actor)

    val userOpt = users.get(actor)
    users.get(actor).fold()(_ => users -= actor)

    sender() !
      (if (users.isEmpty) {
        ChannelEmpty
      } else {
        ChannelNotEmpty
      })

    userOpt
  }

  def receiveEvent: Receive = {
    case AddUser(actor, user) => add(actor, user)
    case RemUser(actor) => rem(actor)
    case Terminated(actor) => rem(actor)
    case CheckSize => sender() ! ChannelSize(users.size)
    case ChannelsCommand(actor) =>
      if (users.nonEmpty) { // Stale channels may occur during split
        /*
          1. Synced servers -> user joins new channel
          2. Servers split
          3. user leaves new channel
          4. Servers resync

          Server that split won't see that remote user left the new channel and so he will still have old state
          with 0 users (since ChannelsUserLoad will be 0)
         */
        actor ! UserInfo(CHANNEL_INFO(name, users.size))
      }
    case WhoCommandToChannel(actor, user) => whoCommand(actor, user)
    case UpdatePing(ping) =>
      val userActor = sender()
      users.get(userActor).fold(/* ??? */)(user => {
        val newUser = user.copy(ping = ping)
        users += userActor -> newUser
        userActor ! UserUpdated(newUser)
        sendUserUpdate(newUser)
      })
    case event =>
  }

  def sendUserUpdate(user: User) = {
    localUsers ! UserFlags(user)
  }

  def whoCommand(actor: ActorRef, user: User) = {
    val usernames = users
      .values
      .map(user => {
        if (Flags.isOp(user)) {
          s"[${user.name.toUpperCase}]"
        } else {
          user.name
        }
      })
      .grouped(2)
      .map(_.mkString(", "))

    actor ! UserInfo(WHO_CHANNEL(name))
    usernames.foreach(actor ! UserInfo(_))
  }
}
