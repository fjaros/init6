package com.init6.channels

import java.util.concurrent.TimeUnit

import akka.actor.{ActorRef, Address, Props}
import akka.util.Timeout
import com.init6.Constants._
import com.init6.{Init6RemotingActor, SystemContext}
import com.init6.channels.utils.{LocalUsersSet, RemoteMultiMap}
import com.init6.coders.Base64
import com.init6.coders.commands._
import com.init6.servers.{Remotable, ServerOnline, SplitMe}
import com.init6.users.{GetUsers, UpdatePing, UserChannelChanged, UserUpdated}

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
      case "vile" => LockedChannelActor("ViLe", "Channel ViLe has been locked. Please use our new chatting channel: init 6")
      case "the void" => VoidedChannelActor("The Void")
      case "init 6" => PublicChannelActor("init 6")
      case "chat" => PublicLimitlessChannelActor("Chat")
      case _ => PrivateChannelActor(name)
    }
  })
}

case class User(
  // Static variables
  ipAddress: String,
  name: String,
  flags: Long = 0,
  ping: Long = 0,
  client: String = "CHAT",
  place: Int = 0,

  // Changeable
  inChannel: String = "",
  channelTimestamp: Long = 0
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
case class WhoCommandResponse(whoResponseMessage: Option[String], userMessages: Seq[String]) extends Command
case class WhoCommandError(errorMessage: String) extends Command

trait ChannelActor extends Init6RemotingActor {

  val name: String

  override val actorPath = s"$INIT6_CHANNELS_PATH/${Base64(name.toLowerCase)}"

  val limit = 200

  // Set of users in this channel on this server
  val localUsers = LocalUsersSet()

  // Map of actor -> user. Actor can be local or remote.
  val users = mutable.LinkedHashMap[ActorRef, User]()
  val remoteUsersMap = RemoteMultiMap[Address, ActorRef]()

  var isSplit = false

  // Settable topic by operator
  var topicExchange = TopicExchange()
  var creationTime: Long = _


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

    if (isLocal(actor)) {
      if (creationTime == 0) {
        creationTime = getAcceptingUptime.toNanos
      }
      localUsers += actor
    } else {
      remoteUsersMap += actor.path.address -> actor
    }

    val newUser =
      if (isLocal(actor)) {
        user.copy(inChannel = name, channelTimestamp = System.currentTimeMillis)
      } else {
        user.copy(inChannel = name)
      }

    log.info("#ADD " + actor + " - " + newUser)
    users += actor -> newUser

    if (isLocal()) {
      //println("sender " + sender())
      sender() ! UserAddedToChannel(newUser, name, self, topicExchange)
      topCommandActor ! UserChannelChanged(actor, newUser)
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
    userOpt.foreach(user => {
      log.info("#REM " + actor + " - " + userOpt + " - " + sender() + " - " + user.channelTimestamp)
      users -= actor
    })

    // clear topic if applicable
    if (users.isEmpty) {
      topicExchange = TopicExchange()
      creationTime = 0
    }

    userOpt
  }

  def remoteIn(remoteUserActor: ActorRef, user: User) = {
    //println("#REMOTEIN " + remoteChannelActor + " - " + remoteUserActor + " - " + user + " - " + users.contains(remoteUserActor))
    users.get(remoteUserActor).fold({
      // new user
      log.info("#RADD " + remoteUserActor + " - " + user)
      users += remoteUserActor -> user

      remoteUsersMap += remoteUserActor.path.address -> remoteUserActor
      localUsers ! UserIn(user)
    })(currentUser => {
      // existing but have to honor the flags of remote
      log.info("#RMOD " + currentUser + " -> " + user)
      users += remoteUserActor -> user
      if (currentUser.flags != user.flags) {
        sendUserUpdate(user)
      }
    })
  }

  // No. On Start advertise to remotes that you are alive.
  override protected def onServerAlive(address: Address) = {
    log.info("#Received ServerAlive {} in channel {}", address, name)
    sendGetChannelUsers(address)
  }

  override protected def onServerDead(address: Address) = {
    log.info("#Received ServerDead {} in channel {}", address, name)
    // call toSeq to make immutable so we're not calling rem during a loop over an iterator
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
      remoteIn(actor, user)

//    case UserToChannelPing =>
//      usersKeepAlive += sender() -> System.currentTimeMillis()

    case GetChannelUsers =>
      log.info("RECEIVED GetChannelUsers from " + sender())
      if (isRemote()) {
        log.info("SENDING ReceivedChannelUsers\n" + users.filterKeys(localUsers.contains).toSeq)
        sender() ! ReceivedChannelUsers(users.filterKeys(localUsers.contains).toSeq, topicExchange)
        if (!remoteActors.contains(remoteActorSelection(sender().path.address))) {
          sender() ! GetChannelUsers
          remoteActors += remoteActorSelection(sender().path.address)
        }
      }

    case ReceivedChannelUsers(remoteUsers, topicExchange) =>
      log.info("RECEIVED ReceivedChannelUsers from " + sender() + "\nremoteUsers: " + remoteUsers)
      remoteUsers.foreach {
        case (actor, user) =>
          remoteIn(actor, user)
      }
      // Replace topic only if timestamp is newer
      if (topicExchange.timestamp > this.topicExchange.timestamp) {
        this.topicExchange = topicExchange
      }

    case GetUsers =>
      //println("RECEIVED GetUsers from " + sender())
      //println(users + " - " + userJoinTimes)
      users
        .values
        .foreach(sender() ! UserIn(_))

    case c@ AddUser(actor, user) => add(actor, user)
    case RemUser(actor) => rem(actor)
    case CheckSize => sender() ! ChannelSize(self, name, users.size)
    case ChannelsCommand => sender() ! ChannelInfo(name, users.size, topicExchange.topic, creationTime)
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
      log.debug("#WHO USERS " + users + "\n#WHO LOCALUSERS: " + localUsers + "\n#WHO REMOTEUSERSEMAP " + remoteUsersMap)
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
        .toSeq

      actor ! WhoCommandResponse(Some(WHO_CHANNEL(name, users.size)), usernames)
    } else {
      actor ! WhoCommandResponse(None, Seq.empty)
    }
  }
}
