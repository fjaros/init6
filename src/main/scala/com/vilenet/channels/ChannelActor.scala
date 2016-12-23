package com.vilenet.channels

import akka.actor.{ActorRef, Props, Terminated}
import com.vilenet.Constants._
import com.vilenet.ViLeNetActor
import com.vilenet.channels.utils.LocalUsersSet
import com.vilenet.coders.commands.{ChannelInfo, ChannelsCommand, Command, WhoCommandToChannel}
import com.vilenet.users.{UpdatePing, UserUpdated}
import com.vilenet.utils.CaseInsensitiveHashMap

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
case class ChannelSize(actor: ActorRef, name: String, size: Int) extends Command

trait ChannelActor extends ViLeNetActor {

  val name: String

  val limit = 200

  // Set of users in this channel on this server
  val localUsers = LocalUsersSet()

  // Linked Map of actor -> user. Actor can be local or remote.
  val users = mutable.LinkedHashMap[ActorRef, User]()

  // Final. Should not be overriden in subclasses. Use receiveEvent to avoid calling super to an abstract declaration
  override final def receive: Receive = {
    case event => receiveEvent(event)
  }

  def add(actor: ActorRef, user: User): User = {
    val newUser = user.copy(channel = name)

    users += actor -> newUser
    sender() ! UserChannel(newUser, name, self)
    newUser
  }

  def rem(actor: ActorRef): Option[User] = {
    val userOpt = users.get(actor)
    userOpt.foreach(_ => users -= actor)

    userOpt
  }

  def receiveEvent: Receive = {
    case AddUser(actor, user) => add(actor, user)
    case RemUser(actor) => rem(actor)
    case CheckSize => sender() ! ChannelSize(self, name, users.size)
    case ChannelsCommand => sender() ! ChannelInfo(name, users.size)
    case c@ WhoCommandToChannel(actor, user) =>
      println(c)
      whoCommand(actor, user)
    case UpdatePing(ping) =>
      val userActor = sender()
      users.get(userActor).foreach(user => {
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

    println(usernames)

    actor ! UserInfo(WHO_CHANNEL(name))
    usernames.foreach(actor ! UserInfo(_))
  }
}
