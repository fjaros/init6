package com.vilenet.users

import java.util.concurrent.TimeUnit

import akka.actor.{ActorRef, Address, Props}
import akka.util.Timeout
import com.vilenet.channels.utils.{LocalUsersSet, RemoteMultiMap}
import com.vilenet.coders.commands._
import com.vilenet.servers._
import com.vilenet._
import com.vilenet.channels._
import com.vilenet.Constants._
import com.vilenet.utils.RealKeyedCaseInsensitiveHashMap

import scala.collection.mutable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success}

/**
 * Created by filip on 9/28/15.
 */
case class Add(connection: ActorRef, user: User, protocol: Protocol) extends Command
case class Rem(userActor: ActorRef) extends Command with Remotable
case class RemActors(userActors: Set[ActorRef]) extends Command

case class WhisperTo(user: User, username: String, message: String)  extends Command
case object SubscribeAll

object UsersActor extends ViLeNetComponent {
  def apply() = system.actorOf(Props[UsersActor], VILE_NET_USERS_PATH)
}

trait Protocol extends Command
case object BinaryProtocol extends Protocol
case object TelnetProtocol extends Protocol
case object Chat1Protocol extends Protocol

case object GetUsers extends Command
case object GetUptime extends Command
case class ReceivedUptime(actor: ActorRef, uptime: Long) extends Command
case class ReceivedUser(user: (String, ActorRef)) extends Command
case class ReceivedUsers(users: Seq[(String, ActorRef)]) extends Command
case class UserToChannelCommandAck(userActor: ActorRef, realUsername: String, command: UserToChannelCommand) extends Command with Remotable
case class UsersUserAdded(userActor: ActorRef, user: User) extends Command

class UsersActor extends ViLeNetRemotingActor with VileNetLoggingActor {

  override val actorPath = VILE_NET_USERS_PATH

  TopCommandActor()

  var placeCounter = 1

  val users = RealKeyedCaseInsensitiveHashMap[ActorRef]()
  val reverseUsers = mutable.HashMap[ActorRef, String]()
  val localUsers = LocalUsersSet()
  val remoteUsersMap = RemoteMultiMap[Address, ActorRef]()

  private def sendGetUsers(address: Address): Unit = {
    remoteActorSelection(address).resolveOne(Timeout(2, TimeUnit.SECONDS).duration).onComplete {
      case Success(actor) =>
        actor ! GetUsers

      case Failure(ex) =>
        system.scheduler.scheduleOnce(Timeout(500, TimeUnit.MILLISECONDS).duration)(sendGetUsers(address))
    }
  }

  def localAdd(connectionActor: ActorRef, user: User, protocol: Protocol) = {
    val newUser = getRealUser(user).copy(place = placeCounter)
    placeCounter += 1

    // Kill any existing actors for this user (can be remote)
    rem(newUser.name)

    // Create new user actor
    val userActor = context.actorOf(UserActor(connectionActor, newUser, protocol))

    // Add to structures
    //println("#ADD " + userActor + " - " + newUser.name)
    users += newUser.name -> userActor
    reverseUsers += userActor -> newUser.name
    localUsers += userActor

    remoteActors.foreach(_ ! Add(userActor, user, protocol))

    // reply to sender
    sender() ! UsersUserAdded(userActor, newUser)

    // place in Top
    topCommandActor ! Add(userActor, newUser, protocol)
  }

  def remoteAdd(actor: ActorRef, username: String): Unit = {
    // Kill any existing actors for this user (can be remote)
    //println("#REMOTEADD " + actor + " - " + username + " - " + reverseUsers.contains(actor))
    if (!reverseUsers.contains(actor)) {
      rem(username)

      remoteUsersMap += actor.path.address -> actor
      reverseUsers += actor -> username
      users += username -> actor
    }
  }

  // Remove by actor
  def rem(actor: ActorRef): Unit = {
    //println("#REM " + actor + " - " + reverseUsers.get(actor))
    reverseUsers.get(actor).foreach(name => {
      if (isLocal(actor)) {
        localUsers -= actor
      } else {
        remoteUsersMap -= actor.path.address -> actor
      }

      reverseUsers -= actor
      users -= name
    })
  }

  // Remove by user
  // KillConnection so it doesn't get inherently replaced in users map
  // Refactor later? Sends 2x rem(actor) event
  def rem(username: String): Unit = {
    users.get(username)
      .map {
        case (_, actor) =>
          actor ! KillConnection
          actor
      }
      .foreach(rem)
  }

  def removeAllRemote(address: Address) = {
    remoteUsersMap.get(address).foreach(actors => {
      users --= actors.flatMap(reverseUsers.get)
      reverseUsers --= actors
      remoteUsersMap -= address
    })
  }

  override protected def onServerAlive(address: Address) = {
    log.error("onServerAlive {}", address)
    sendGetUsers(address)
  }

  override protected def onServerDead(address: Address) = {
    removeAllRemote(address)
  }

  override def loggedReceive: Receive = {
    case ServerOnline =>
//      publish(TOPIC_USERS, GetUsers)

    case GetUsers =>
      if (isRemote()) {
        sender() ! ReceivedUsers(
          localUsers
            .map(actor => reverseUsers(actor) -> actor)
            .toSeq
        )
        if (!remoteActors.contains(remoteActorSelection(sender().path.address))) {
          sender() ! GetUsers
          remoteActors += remoteActorSelection(sender().path.address)
        }
      }

    case c@ ReceivedUsers(remoteUsers) =>
//      println(c)
      if (isRemote()) {
        remoteUsers.foreach {
          case (username, actor) => remoteAdd(actor, username)
        }
      }

    case command: UserToChannelCommand =>
      sender() ! users.get(command.toUsername)
        .fold[Command](UserError(Constants.USER_NOT_LOGGED_ON)) {
          case (name, actor) => UserToChannelCommandAck(actor, name, command)
      }

    case command: UserCommand =>
      //log.error(s"command sending $command")
      users.get(command.toUsername)
        .fold(sender() ! UserError(Constants.USER_NOT_LOGGED_ON)) {
          case (_, actor) =>
            ////log.error(s"users $users")
            //log.error(s"sending to $x from ${sender()}")
            actor ! (sender(), command)
          }

    case UsersCommand =>
      println("=== LOCALUSERS " + localUsers.size)
      println(localUsers)
      println("=== REVERSEUSERS " + reverseUsers.size)
      println(reverseUsers)
      println("=== REMOTEUSERMAP " + remoteUsersMap.values.map(_.size).toSeq)
      println(remoteUsersMap)
      println("=== USERS " + users.size)
      println(users)
      sender() ! UserInfo(USERS(localUsers.size, users.size))

    case event =>
      //log.error(s"event $event from ${sender()}")
      if (isLocal()) {
        handleLocal(event)
      } else {
        handleRemote(event)
      }
  }

  def handleLocal: Receive = {
    case SplitMe =>
      if (isLocal()) {
        users
          .filterNot {
            case (_, (_, actor)) =>
              localUsers.contains(actor)
          }
          .foreach {
            case (_, (_, actor)) =>
              rem(actor)
          }
      } else {
        removeAllRemote(sender().path.address)
      }

    case Add(connection, user, protocol) =>
      localAdd(connection, user, protocol)

    case Rem(userActor) =>
      rem(userActor)

    case BroadcastCommand(message) =>
      localUsers ! UserError(message)

    case DisconnectCommand(user) =>
      rem(user)

    case x =>
      log.error("UsersActor Unhandled Local {}", x)
  }

  def handleRemote: Receive = {
    case Add(userActor, user, _) =>
      remoteAdd(userActor, user.name)

    case Rem(userActor) =>
      rem(userActor)

    case c @ RemActors(userActors) =>
      userActors.foreach(rem)

    case BroadcastCommand(message) =>
      //println(s"### Remote Broadcast $localUsers")
      localUsers ! UserError(message)

    case x =>
      log.error("UsersActor Unhandled Remote {}", x)
  }

  def getRealUser(user: User): User = {
    if (Config().Accounts.enableMultipleLogins) {
      var number = 1
      var username = user.name
      while (users.contains(username)) {
        number = number + 1
        username = s"${user.name}#$number"
      }
      user.copy(name = username)
    } else {
      user
    }
  }
}
