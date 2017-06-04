package com.init6.channels

import java.util.concurrent.TimeUnit

import akka.actor.{ActorRef, Address, Props}
import akka.util.Timeout
import com.init6.Constants._
import com.init6.channels.utils.{LocalUsersSet, RemoteMultiMap}
import com.init6.coders.Base64
import com.init6.coders.commands._
import com.init6.db.DbChannelJoin
import com.init6.servers.Remotable
import com.init6.users.{GetUsers, UpdatePing, UserUpdated}
import com.init6.utils.CaseInsensitiveHashMap
import com.init6.{Config, Init6RemotingActor, SystemContext}

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
      //case "vile" => LockedChannelActor("ViLe", "Channel ViLe has been locked. Please use our new chatting channel: init 6")
      case "the void" => VoidedChannelActor("The Void")
      case "init 6" => PublicChannelActor("init 6")
      case "chat" => PublicLimitlessChannelActor("Chat")
      //case "dark" => new DiabOTChannelActor(name)
      //case "andariel" | "duriel" | "belial" | "azmodan" => new LesserEvilChannelActor(name)
      //case "deckard cain" => new DeckardCainChannelActor("Deckard Cain")
      case _ => PrivateChannelActor(name)
    }
  })
}

case class User(
  // Static variables
  id: Long,
  aliasId: Option[Long],
  ipAddress: String,
  name: String,
  flags: Long = 0,
  ping: Long = 0,
  client: String = "CHAT",

  // Changeable
  inChannel: String = "",
  channelTimestamp: Long = 0
) extends Command

case class TopicExchange(
  topic: String = "",
  timestamp: Long = 0
) extends Command

case class AddUser(actor: ActorRef, user: User, connectionTimestamp: Long) extends Command
case class RemUser(actor: ActorRef) extends Command with Remotable
case class UserAddedToChannel(user: User, channelName: String, channelFlags: Long, channelActor: ActorRef, topicExchange: TopicExchange, channelSize: Int)
case object CheckSize extends Command
case class ChannelSize(actor: ActorRef, name: String, size: Int) extends Command
case object ChannelToUserPing extends Command
case object UserToChannelPing extends Command
case class InternalChannelUserUpdate(actor: ActorRef, user: User) extends Command
case class ChannelJoinResponse(message: ChatEvent) extends Command
case class WhoCommandResponse(whoResponseMessage: Option[String], userMessages: Seq[String]) extends Command
case class WhoCommandError(errorMessage: String) extends Command
case class PrintChannelUsersResponse(chatEvent: ChatEvent) extends Command

trait ChannelActor extends Init6RemotingActor {

  val name: String
  val flags: Long = 0x00

  override val actorPath = s"$INIT6_CHANNELS_PATH/${Base64(name.toLowerCase)}"

  val limit = Config().Channels.limit

  // Set of users in this channel on this server
  val localUsers = LocalUsersSet()

  // Map of actor -> user. Actor can be local or remote.
  val users = mutable.LinkedHashMap[ActorRef, User]()
  val reverseUsernames = CaseInsensitiveHashMap[ActorRef]()
  val remoteUsersMap = RemoteMultiMap[Address, ActorRef]()

  var isSplit = false

  // Settable topic by operator
  var topicExchange = TopicExchange()
  var creationTime: Long = _
  var joinedUsers: Int = 1


  private def sendGetChannelUsers(address: Address): Unit = {
    import context.dispatcher

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
    rem(actor)
    rem(user)

    var joinedTime: Long = 0
    if (isLocal(actor)) {
      if (creationTime == 0) {
        creationTime = getAcceptingUptime.toNanos
        joinedTime = creationTime
      } else {
        joinedTime = getAcceptingUptime.toNanos
      }
      localUsers += actor
    } else {
      remoteUsersMap += actor.path.address -> actor
    }

    val newUser =
      if (isLocal(actor)) {
        // later
        if (user.id > 0 && joinedTime - creationTime < 1000000000) {
          daoActor ! DbChannelJoin(
            server_id = Config().Server.serverId,
            user_id = user.id,
            alias_id = user.aliasId,
            channel = name.toLowerCase,
            server_accepting_time = SystemContext.startMillis,
            channel_created_time = creationTime,
            joined_time = joinedTime,
            joined_place = joinedUsers,
            is_operator = Flags.isOp(user)
          )
        }
        Flags.deSpecialOp(user.copy(inChannel = name, channelTimestamp = System.currentTimeMillis))
      } else {
        Flags.deSpecialOp(user.copy(inChannel = name))
      }

    log.info("#ADD " + actor + " - " + newUser)
    users += actor -> newUser
    reverseUsernames += newUser.name -> actor

    if (isLocal()) {
      sender() ! UserAddedToChannel(newUser, name, flags, self, topicExchange, joinedUsers)
      joinedUsers += 1
    }

    newUser
  }

  def rem(user: User): Unit = {
    reverseUsernames
      .get(user.name)
      .foreach(actor => {
        rem(actor)
        reverseUsernames -= user.name
      })
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
      reverseUsernames -= user.name
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
    users.get(remoteUserActor).fold[Unit]({
      log.info("#RADD " + remoteUserActor + " - " + user)
      add(remoteUserActor, user)
    })(currentUser => {
      // existing but have to honor the flags of remote
      log.info("#RMOD " + currentUser + " -> " + user)
      users += remoteUserActor -> user
      if (currentUser.flags != user.flags) {
        sendUserUpdate(remoteUserActor, user)
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
    case InternalChannelUserUpdate(actor, user) =>
      if (users.contains(actor)) {
        remoteIn(actor, user)
      } else {
        log.info("###ICUU ignored in " + name + " - " + actor + " - "  + user)
      }

    case GetChannelUsers =>
      if (isRemote()) {
        log.info("SENDING ReceivedChannelUsers: " + users.filterKeys(localUsers.contains).toSeq)
        sender() ! ReceivedChannelUsers(users.filterKeys(localUsers.contains).toSeq, topicExchange)
        if (!remoteActors.contains(remoteActorSelection(sender().path.address))) {
          sender() ! GetChannelUsers
          remoteActors += remoteActorSelection(sender().path.address)
        }
      }

    case ReceivedChannelUsers(remoteUsers, topicExchange) =>
      log.info("RECEIVED ReceivedChannelUsers from " + sender() + ": " + remoteUsers)
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
      val userActor = sender()
      users
        .values
        .foreach(userActor ! UserIn(_))

    case c@ AddUser(actor, user, _) => add(actor, user)
    case RemUser(actor) => rem(actor)
    case CheckSize => sender() ! ChannelSize(self, name, users.size)
    case ChannelsCommand => sender() ! ChannelInfo(name, users.size, topicExchange.topic, creationTime)
    case c@ WhoCommandToChannel(actor, user, opsOnly) => whoCommand(actor, user, opsOnly)
    case UpdatePing(ping) =>
      val userActor = sender()
      users.get(userActor).foreach(user => {
        val newUser = user.copy(ping = ping)
        users += userActor -> newUser
        userActor ! UserUpdated(newUser)

        // Remove for shitty bnet bots
        //sendUserUpdate(newUser)
      })
    case PrintChannelUsers(_) =>
      sender() ! PrintChannelUsersResponse(
        UserInfoArray(Array(
          "Users: " + users,
          "LocalUsers: " + localUsers,
          "RemoteUsersMap: " + remoteUsersMap,
          "ReverseUsernames: " + reverseUsernames
        ))
      )
    case event =>
      //println("Unhandled ChannelActor " + event)
  }

  def sendUserUpdate(actor: ActorRef, user: User) = {
    if (isLocal(actor)) {
      actor ! UserUpdated(user)
    }
    localUsers ! UserFlags(user)
  }

  def whoCommand(actor: ActorRef, user: User, opsOnly: Boolean) = {
    actor !
      (if (users.nonEmpty) {
        if (opsOnly) {
          handleOpsCommand()
        } else {
          handleWhoCommand()
        }
      } else {
        WhoCommandResponse(None, Seq.empty)
      })
  }

  private def handleOpsCommand() = {
    val ops = users
      .values
      .filter(Flags.isOp)

    val usernames = ops
      .map(user => s"[${user.name.toUpperCase}]")
      .grouped(2)
      .map(_.mkString(", "))
      .toSeq

    WhoCommandResponse(Some({
      if (usernames.nonEmpty) {
        OPS_CHANNEL(name, ops.size)
      } else {
        OPS_NOT_EXIST(name)
      }
    }), usernames)
  }

  private def handleWhoCommand() = {
    val (ops, noOps) = users
      .values
      .foldLeft((Seq.empty[String], Seq.empty[String])) {
        case ((ops, noOps), user) =>
          if (Flags.isOp(user)) {
            (ops :+ s"[${user.name.toUpperCase}]", noOps)
          } else {
            (ops, noOps :+ user.name)
          }
      }

    val usernames = (ops ++ noOps)
      .grouped(2)
      .map(_.mkString(", "))
      .toSeq

    WhoCommandResponse(Some(WHO_CHANNEL(name, users.size)), usernames)
  }
}
