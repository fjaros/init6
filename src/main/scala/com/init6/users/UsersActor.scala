package com.init6.users

import java.net.InetSocketAddress
import java.util.concurrent.TimeUnit

import akka.actor.{ActorRef, Address, Props}
import akka.util.Timeout
import com.init6.Constants._
import com.init6._
import com.init6.channels._
import com.init6.channels.utils.{LocalUsersSet, RemoteMultiMap}
import com.init6.coders.IPUtils
import com.init6.coders.commands._
import com.init6.connection.ConnectionInfo
import com.init6.servers._
import com.init6.utils.RealKeyedCaseInsensitiveHashMap

import scala.collection.mutable
import scala.concurrent.duration.Duration
import scala.util.{Failure, Success}

/**
 * Created by filip on 9/28/15.
 */
case class Add(connectionInfo: ConnectionInfo, user: User, protocol: Protocol) extends Command
case class RemoteAdd(userActor: ActorRef, username: String) extends Command
case class Rem(ipAddress: InetSocketAddress, userActor: ActorRef) extends Command with Remotable
case class RemActors(userActors: Set[ActorRef]) extends Command

case class WhisperTo(user: User, username: String, message: String)  extends Command
case object SubscribeAll

object UsersActor extends Init6Component {
  def apply() = system.actorOf(Props[UsersActor].withDispatcher(USERS_DISPATCHER), INIT6_USERS_PATH)
}

trait Protocol extends Command
case object NotYetKnownProtocol extends Protocol
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
case class UsersUserNotAdded() extends Command

class UsersActor extends Init6RemotingActor with Init6LoggingActor {

  override val actorPath = INIT6_USERS_PATH

  val users = RealKeyedCaseInsensitiveHashMap[ActorRef]()
  val reverseUsers = mutable.HashMap[ActorRef, String]()
  val localUsers = LocalUsersSet()
  val remoteUsersMap = RemoteMultiMap[Address, ActorRef]()

  val ipLimitMap = mutable.HashMap[Int, Int]()

  val placeMap = mutable.SortedMap[Long, Int]()

  private def sendGetUsers(address: Address): Unit = {
    import context.dispatcher

    remoteActorSelection(address).resolveOne(Timeout(2, TimeUnit.SECONDS).duration).onComplete {
      case Success(actor) =>
        actor ! GetUsers

      case Failure(ex) =>
        system.scheduler.scheduleOnce(Timeout(500, TimeUnit.MILLISECONDS).duration)(sendGetUsers(address))
    }
  }

  def localAdd(connectionInfo: ConnectionInfo, user: User, protocol: Protocol) = {
    val newUser = getRealUser(user)

    // Kill any existing actors for this user (can be remote)
    rem(newUser.name)

    // Create new user actor
    val userActor = context.actorOf(UserActor(connectionInfo, newUser, protocol))

    // Add to structures
    //println("#ADD " + userActor + " - " + newUser.name)
    users += newUser.name -> userActor
    reverseUsers += userActor -> newUser.name
    localUsers += userActor

    remoteActors.foreach(_ ! RemoteAdd(userActor, user.name))

    // reply to sender
    sender() ! UsersUserAdded(userActor, newUser)
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
      val _actors = actors.toSeq // just in case (immutability)
      users --= _actors.flatMap(reverseUsers.get)
      reverseUsers --= _actors
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

    case PlaceOnServerCommand(serverIp) =>
      sender() ! UserInfo(SERVER_PLACE(getPlace, Config().Server.host))

    case command @ FriendsWhois(position, who) =>
      users.get(who)
        .fold(sender() ! FriendsWhoisResponse(online = false, position, who, "", "", "")) {
          case (_, actor) =>
            actor ! (sender(), command)
        }

    case command @ WhisperToFriendsMessage(fromUser, toFriends, message) =>
      toFriends.foreach(friend => {
        users.get(friend)
          .foreach {
            case (_, actor) =>
              actor ! (sender(), WhisperMessage(fromUser, friend, message, sendNotification = false))
          }
      })
      sender() ! UserWhisperedTo(fromUser.copy(name = YOUR_FRIENDS), message)

    case UsersCommand =>
//      println("=== LOCALUSERS " + localUsers.size)
//      println(localUsers)
//      println("=== REVERSEUSERS " + reverseUsers.size)
//      println(reverseUsers)
//      println("=== REMOTEUSERMAP " + remoteUsersMap.values.map(_.size).toSeq)
//      println(remoteUsersMap)
//      println("=== USERS " + users.size)
//      println(users)
      val userActor = sender()
      userActor ! UserInfo(USERS_TOTAL(users.size, Config().Server.host))
      (remoteUsersMap
        .toSeq
        .map {
          case (address, userSet) => address.host.getOrElse(UNKNOWN) -> userSet.size
        } :+ (Config().Server.host -> localUsers.size))
        .sortBy {
          case (_, size) => size
        }(Ordering[Int].reverse)
        .foreach {
          case (host, size) => userActor ! UserInfo(USERS(size, host))
        }

    case UptimeCommand =>
      sender() ! UptimeCommand()

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

    case Add(connectionInfo, user, protocol) =>
      // Check if limited
      if (addToLimiter(connectionInfo.ipAddress)) {
        localAdd(connectionInfo, user, protocol)
      } else {
        sender() ! UsersUserNotAdded()
      }

    case Rem(ipAddress, userActor) =>
      rem(userActor)
      removeFromLimiter(ipAddress)

    case BroadcastCommand(message) =>
      localUsers ! UserError(message)

    case BroadcastCommandToLocal(message) =>
      localUsers ! UserError(message)

    case DisconnectCommand(user) =>
      rem(user)

    case IpBanCommand(ipAddress, _) =>
      localUsers ! DisconnectOnIp(ipAddress)

    case PrintLoginLimit =>
      sender() ! UserInfoArray(
        ipLimitMap.map {
          case (ipDword, count) =>
            s"${IPUtils.dwordToString(ipDword)} - $count"
        }.toArray
      )

    case x =>
      log.error("UsersActor Unhandled Local {}", x)
  }

  def handleRemote: Receive = {
    case RemoteAdd(userActor, username) =>
      remoteAdd(userActor, username)

    case Rem(ipAddress, userActor) =>
      rem(userActor)

    case c @ RemActors(userActors) =>
      userActors.foreach(rem)

    case BroadcastCommand(message) =>
      //println(s"### Remote Broadcast $localUsers")
      localUsers ! UserError(message)

    case x =>
      log.error("UsersActor Unhandled Remote {}", x)
  }

  def addToLimiter(ipAddress: InetSocketAddress) = {
    val ipDword = IPUtils.bytesToDword(ipAddress.getAddress.getAddress)
    val count = ipLimitMap.getOrElse(ipDword, 0)

    if (Config().Accounts.loginLimit > count) {
      ipLimitMap += ipDword -> (count + 1)
      true
    } else {
      false
    }
  }

  def removeFromLimiter(ipAddress: InetSocketAddress) = {
    val ipDword = IPUtils.bytesToDword(ipAddress.getAddress.getAddress)

    ipLimitMap
      .get(ipDword)
      .foreach(count => {
        if (count > 0) {
          ipLimitMap += ipDword -> (count - 1)
        }
      })
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
