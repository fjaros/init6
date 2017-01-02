package com.vilenet.channels

import java.util.concurrent.TimeUnit

import akka.actor.{ActorRef, Address, Props}
import akka.cluster.ClusterEvent.UnreachableMember
import akka.util.Timeout
import com.vilenet.Constants._
import com.vilenet.ViLeNetClusterActor
import com.vilenet.channels.utils.{LocalUsersSet, RemoteMultiMap}
import com.vilenet.coders.commands._
import com.vilenet.users.{GetUsers, UpdatePing, UserUpdated}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.collection.mutable
import scala.util.Try

/**
  * Created by filip on 11/12/15.
  */
object ChannelActor {
  // Channel Factory
  def apply(name: String) = Props({
    name.toLowerCase match {
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
                 place: Int = 0,

                 // Changeable
                 channel: String = ""
               ) extends Command

case class AddUser(actor: ActorRef, user: User) extends Command
case class RemUser(actor: ActorRef) extends Command
case class UserAddedToChannel(user: User, channelName: String, channelActor: ActorRef, channelTopic: String)
case object CheckSize extends Command
case class ChannelSize(actor: ActorRef, name: String, size: Int) extends Command
case object ChannelPing extends Command
case object ChannelToUserPing extends Command
case object UserToChannelPing extends Command
case class InternalChannelUserUpdate(actor: ActorRef, user: User) extends Command


trait ChannelActor extends ViLeNetClusterActor {

  val name: String

  val limit = 200

  // Set of users in this channel on this server
  val localUsers = LocalUsersSet()

  // Linked Map of actor -> user. Actor can be local or remote.
  val users = mutable.LinkedHashMap[ActorRef, User]()

  val remoteUsersMap = RemoteMultiMap[Address, ActorRef]()
  val usersKeepAlive = mutable.HashMap[ActorRef, Long]()
  val remoteAddressReceived = mutable.HashSet[ActorRef]()

  // Settable topic by operator
  var topic = ""

  val pubSubTopic = TOPIC_CHANNEL(name)
  if (subscribe(pubSubTopic)) {
    system.scheduler.schedule(
      Timeout(1000, TimeUnit.MILLISECONDS).duration,
      Timeout(1000, TimeUnit.MILLISECONDS).duration
    ) {
      publish(pubSubTopic, ChannelPing)

      val now = System.currentTimeMillis()
      usersKeepAlive.foreach {
        case (actor, time) =>
          if (now - time >= 4000) {
            rem(actor)
          } else {
            actor ! ChannelToUserPing
          }
      }
    }
  } else {
    log.error("Failed to subscribe to {}", pubSubTopic)
  }

  // Final. Should not be overriden in subclasses. Use receiveEvent to avoid calling super to an abstract declaration
  override final def receive: Receive = {
    case event => receiveEvent(event)
  }

  def add(actor: ActorRef, user: User): User = {
    val newUser = user.copy(channel = name)

    if (isLocal(actor)) {
      localUsers += actor
    } else {
      remoteUsersMap += actor.path.address -> actor
    }
    users += actor -> newUser
    usersKeepAlive += actor -> System.currentTimeMillis()

    sender() ! UserAddedToChannel(newUser, name, self, topic)
    newUser
  }

  def rem(actor: ActorRef): Option[User] = {
    println("REM " + actor)
    //Try(throw new Exception("shit")).failed.get.printStackTrace()
    if (isLocal(actor)) {
      localUsers -= actor
    } else {
      remoteUsersMap -= actor.path.address -> actor
    }
    val userOpt = users.get(actor)
    userOpt.foreach(_ => {
      users -= actor
      usersKeepAlive -= actor
    })

    userOpt
  }

  def remoteIn(remoteChannelActor: ActorRef, remoteUserActor: ActorRef, user: User) = {
    println("### remoteIn")
    println(remoteChannelActor + " - " + remoteUserActor + " - " + user)
    users += remoteUserActor -> user
    usersKeepAlive += remoteUserActor -> System.currentTimeMillis()
    remoteUsersMap += remoteChannelActor.path.address -> remoteUserActor
    localUsers ! UserIn(user)
  }

  def remoteAdd(actor: ActorRef, remoteUser: (ActorRef, User)) = {
    users += remoteUser
    remoteUsersMap += actor.path.address -> remoteUser._1
  }

  def receiveEvent: Receive = {
    case InternalChannelUserUpdate(actor, user) =>
      if (users.contains(actor)) {
        users += actor -> user
        sendUserUpdate(user)
      }

    case UserToChannelPing =>
      usersKeepAlive += sender() -> System.currentTimeMillis()

    case ChannelPing =>
      val remoteChannelActor = sender()
      if (isRemote(remoteChannelActor) && !remoteAddressReceived.contains(remoteChannelActor)) {
        // Received a ping but haven't seen this remote channel actor yet
        remoteChannelActor ! GetChannelUsers
        remoteAddressReceived += remoteChannelActor
      } else {
//        if (isRemote(remoteChannelActor)) {
//          println("## CHANPING")
//          println(remoteUsersMap)
//        }
      }

    case UnreachableMember(member) =>
      //println(remoteUsersMap)
      remoteUsersMap.get(member.address).foreach(_.foreach(rem))
      remoteUsersMap -= member.address

    case GetChannelUsers =>
      println("RECEIVED GetChannelUsers from " + sender())
      println(users)
      if (isRemote()) {
        sender() ! ReceivedChannelUsers(users.filterKeys(localUsers.contains).toSeq)
      }

    case ReceivedChannelUsers(remoteUsers) =>
      println("RECEIVED ReceivedChannelUsers from " + sender())
      println(remoteUsers)
      remoteUsers.foreach {
        case (actor, user) =>
          remoteIn(sender(), actor, user)
      }

    case GetUsers =>
      //println("RECEIVED GetUsers from " + sender())
      //println(users)
      users.foreach {
        case (_, user) =>
          sender() ! UserIn(user)
      }

    case AddUser(actor, user) => add(actor, user)
    case RemUser(actor) => rem(actor)
    case CheckSize => sender() ! ChannelSize(self, name, users.size)
    case ChannelsCommand => sender() ! ChannelInfo(name, users.size, topic)
    case c@ WhoCommandToChannel(actor, user) => whoCommand(actor, user)
    case UpdatePing(ping) =>
      val userActor = sender()
      users.get(userActor).foreach(user => {
        val newUser = user.copy(ping = ping)
        users += userActor -> newUser
        userActor ! UserUpdated(newUser)
        sendUserUpdate(newUser)
      })
    case event =>
      //println("Unhandled ChannelActor " + event)
  }

  def sendUserUpdate(user: User) = {
    localUsers ! UserFlags(user)
  }

  def whoCommand(actor: ActorRef, user: User) = {
    if (users.nonEmpty) {
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
    } else {
      actor ! UserErrorArray(CHANNEL_NOT_EXIST)
    }
  }
}
