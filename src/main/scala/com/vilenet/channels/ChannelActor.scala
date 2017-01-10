package com.vilenet.channels

import java.util.concurrent.TimeUnit

import akka.actor.{ActorRef, Address, Props}
import akka.util.Timeout
import com.vilenet.Constants._
import com.vilenet.ViLeNetRemotingActor
import com.vilenet.channels.utils.{LocalUsersSet, RemoteMultiMap}
import com.vilenet.coders.Base64
import com.vilenet.coders.commands._
import com.vilenet.servers.{Remotable, ServerOnline, SplitMe}
import com.vilenet.users.{GetUsers, UpdatePing, UserUpdated}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.collection.mutable
import scala.util.{Failure, Success}

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
      case "chat" => PublicLimitlessChannelActor("Chat")
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
  inChannel: String = "",
  joiningChannel: String = ""
) extends Command

case class TopicExchange(
  topic: String = "",
  timestamp: Long = 0
) extends Command

case class AddUser(actor: ActorRef, user: User) extends Command
case class RemUser(actor: ActorRef) extends Command with Remotable
case object IsEmpty
case object NonEmpty
case class UserAddedToChannel(user: User, channelName: String, channelActor: ActorRef, topicExchange: TopicExchange)
case object CheckSize extends Command
case class ChannelSize(actor: ActorRef, name: String, size: Int) extends Command
case object ChannelToUserPing extends Command
case object UserToChannelPing extends Command
case class InternalChannelUserUpdate(actor: ActorRef, user: User) extends Command
case class ChannelJoinResponse(message: ChatEvent) extends Command

trait ChannelActor extends ViLeNetRemotingActor {

  val name: String

  override val actorPath = s"$VILE_NET_CHANNELS_PATH/${Base64(name.toLowerCase)}"

  val limit = 200

  // Set of users in this channel on this server
  val localUsers = LocalUsersSet()

  // Linked Map of actor -> user. Actor can be local or remote.
  val users = mutable.LinkedHashMap[ActorRef, User]()

  val remoteUsersMap = RemoteMultiMap[Address, ActorRef]()
  //val usersKeepAlive = mutable.HashMap[ActorRef, Long]()
  //val remoteAddressReceived = mutable.HashSet[Address]()

  var isSplit = false

  // Settable topic by operator
  var topicExchange = TopicExchange()

  private def sendGetChannelUsers(address: Address): Unit = {
    remoteActorSelection(address).resolveOne(Timeout(2, TimeUnit.SECONDS).duration).onComplete {
      case Success(actor) =>
        actor ! GetChannelUsers

      case Failure(ex) =>
        system.scheduler.scheduleOnce(Timeout(500, TimeUnit.MILLISECONDS).duration)(sendGetChannelUsers(address))
    }
  }

  // Final. Should not be overriden in subclasses. Use receiveEvent to avoid calling super to an abstract declaration
  override final def receive: Receive = {
    case event => receiveEvent(event)
  }

  def add(actor: ActorRef, user: User): User = {
    // just in case
    //println("#ADD " + actor + " - " + user + " - " + sender())
    rem(actor)

    val newUser = user.copy(inChannel = name)

    if (isLocal(actor)) {
      localUsers += actor
    } else {
      remoteUsersMap += actor.path.address -> actor
    }
    users += actor -> newUser
    //usersKeepAlive += actor -> System.currentTimeMillis()

    if (isLocal()) {
      //println("sender " + sender())
      sender() ! UserAddedToChannel(newUser, name, self, topicExchange)
    }
    newUser
  }

  def rem(actor: ActorRef): Option[User] = {
    if (isLocal(actor)) {
      localUsers -= actor
    } else {
      remoteUsersMap -= actor.path.address -> actor
    }
    val userOpt = users.get(actor)
    //println("#REM " + actor + " - " + userOpt + " - " + sender())
    userOpt.foreach(_ => {
      users -= actor
      //usersKeepAlive -= actor
    })

    userOpt
  }

  def remoteIn(remoteChannelActor: ActorRef, remoteUserActor: ActorRef, user: User) = {
    //println("#REMOTEIN " + remoteChannelActor + " - " + remoteUserActor + " - " + user + " - " + users.contains(remoteUserActor))
    if (!users.contains(remoteUserActor)) {
      users += remoteUserActor -> user
      //usersKeepAlive += remoteUserActor -> System.currentTimeMillis()
      remoteUsersMap += remoteChannelActor.path.address -> remoteUserActor
      localUsers ! UserIn(user)
    }
  }

  // No. On Start advertise to remotes that you are alive.
  override protected def onServerAlive(address: Address) = {
    log.info("#Received ServerAlive {} in channel {}", address, name)
    sendGetChannelUsers(address)
  }

  override protected def onServerDead(address: Address) = {
    log.info("#Received ServerDead {} in channel {}", address, name)
    // call toSet to make immutable so we're not calling rem during a loop over an iterator
    remoteUsersMap.get(address).foreach(_.toSeq.foreach(rem))
    remoteUsersMap -= address
  }

  def receiveEvent: Receive = {
//    case SplitMe =>
//      if (isLocal()) {
//        isSplit = true
//        remoteUsersMap
//          .values
//          .flatten
//          .foreach(rem)
//
//        unsubscribe(pubSubTopic)
//      } else {
//        val remoteAddress = sender().path.address
//        remoteUsersMap
//          .get(remoteAddress)
//          .foreach(_.foreach(rem))
//        remoteAddressReceived -= remoteAddress
//      }

    case ServerOnline =>
      if (isLocal()) {
        isSplit = false
        //subscribe(pubSubTopic)
      }

    case InternalChannelUserUpdate(actor, user) =>
      if (users.contains(actor)) {
        users += actor -> user
        sendUserUpdate(user)
      }

//    case UserToChannelPing =>
//      usersKeepAlive += sender() -> System.currentTimeMillis()

    case GetChannelUsers =>
      println("RECEIVED GetChannelUsers from " + sender() + "\n" + "users: " + users)
      if (isRemote()) {
        println("SENDING ReceivedChannelUsers\n" + users.filterKeys(localUsers.contains).toSeq)
        sender() ! ReceivedChannelUsers(users.filterKeys(localUsers.contains).toSeq, topicExchange)
        if (!remoteActors.contains(remoteActorSelection(sender().path.address))) {
          sender() ! GetChannelUsers
          remoteActors += remoteActorSelection(sender().path.address)
        }
      }

    case ReceivedChannelUsers(remoteUsers, topicExchange) =>
      println("RECEIVED ReceivedChannelUsers from " + sender() + "\nremoteUsers: " + remoteUsers)
      remoteUsers.foreach {
        case (actor, user) =>
          remoteIn(sender(), actor, user)
      }
      // Replace topic only if timestamp is newer
      if (topicExchange.timestamp > this.topicExchange.timestamp) {
        this.topicExchange = topicExchange
      }

    case GetUsers =>
      //println("RECEIVED GetUsers from " + sender())
      //println(users)
      users.foreach {
        case (_, user) =>
          sender() ! UserIn(user)
      }

    case c@ AddUser(actor, user) => add(actor, user)
    case RemUser(actor) => rem(actor)
    case CheckSize => sender() ! ChannelSize(self, name, users.size)
    case ChannelsCommand => sender() ! ChannelInfo(name, users.size, topicExchange.topic)
    case c@ WhoCommandToChannel(actor, user) => whoCommand(actor, user)
    case UpdatePing(ping) =>
      val userActor = sender()
      users.get(userActor).foreach(user => {
        val newUser = user.copy(ping = ping)
        users += userActor -> newUser
        userActor ! UserUpdated(newUser)

        // Remove for shitty bnet bots
        //sendUserUpdate(newUser)
      })
    case event =>
      //println("Unhandled ChannelActor " + event)
  }

  def sendUserUpdate(user: User) = {
    localUsers ! UserFlags(user)
  }

  def whoCommand(actor: ActorRef, user: User) = {
    if (users.nonEmpty) {
      println("#WHO USERS " + users + "\n#WHO LOCALUSERS: " + localUsers + "\n#WHO REMOTEUSERSEMAP " + remoteUsersMap)
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
