package com.vilenet.users

import java.util.concurrent.TimeUnit

import akka.actor.{ActorRef, Address, Props, Terminated}
import akka.cluster.ClusterEvent.{MemberUp, UnreachableMember}
import akka.cluster.pubsub.DistributedPubSubMediator.{Subscribe, SubscribeAck}
import akka.util.Timeout
import com.vilenet.channels.utils.{LocalUsersSet, RemoteMultiMap}
import com.vilenet.coders.commands._
import com.vilenet.servers._
import com.vilenet.{Config, Constants, ViLeNetClusterActor, ViLeNetComponent}
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
case class Rem(userActor: ActorRef) extends Command
case class RemActors(userActors: Set[ActorRef]) extends Command

case class WhisperTo(user: User, username: String, message: String)  extends Command
case object SubscribeAll

object UsersActor extends ViLeNetComponent {

  def apply() = {
    // Await until all cluster subscriptions are complete
    val usersActor = system.actorOf(Props[UsersActor], VILE_NET_USERS_PATH)
    usersActor
//    implicit val timeout = Timeout(1, TimeUnit.SECONDS)
//    Await.result(usersActor ? SubscribeAll, timeout.duration) match {
//      case SubscribeAll =>
//        println("###1 reply")
//    }
  }
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
case class UserToChannelCommandAck(userActor: ActorRef, realUsername: String, command: UserToChannelCommand) extends Command
case class UsersUserAdded(userActor: ActorRef, user: User) extends Command

class UsersActor extends ViLeNetClusterActor {

  TopCommandActor()

  var placeCounter = 1

  val remoteUsersActor = (address: Address) =>
    system.actorSelection(s"akka://${address.hostPort}/user/$VILE_NET_USERS_PATH")

  val users = RealKeyedCaseInsensitiveHashMap[ActorRef]()
  val reverseUsers = mutable.HashMap[ActorRef, String]()
  val localUsers = LocalUsersSet()
  val remoteUsersMap = RemoteMultiMap[Address, ActorRef]()

  val clusterTopics = mutable.Set(TOPIC_ONLINE, TOPIC_USERS, TOPIC_SPLIT)
  clusterTopics.foreach(subscribe)
  var subscribeAllAckActor: ActorRef = _

  private def sendGetUsers(address: Address): Unit = {
    remoteUsersActor(address).resolveOne(Timeout(5, TimeUnit.SECONDS).duration).onComplete {
      case Success(actor) =>
        actor ! GetUsers

      case Failure(ex) =>
        system.scheduler.scheduleOnce(Timeout(500, TimeUnit.MILLISECONDS).duration, new Runnable {
          override def run(): Unit = sendGetUsers(address)
        })
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
    users += newUser.name -> userActor
    reverseUsers += userActor -> newUser.name
    localUsers += userActor

    // publish to cluster
    publish(TOPIC_USERS, RemoteEvent(Add(userActor, newUser, protocol)))

    // reply to sender
    sender() ! UsersUserAdded(userActor, newUser)
  }

  def remoteAdd(actor: ActorRef, username: String): Unit = {
    // Kill any existing actors for this user (can be remote)
    rem(username)

    remoteUsersMap += actor.path.address -> actor
    reverseUsers += actor -> username
    users += username -> actor
  }

  // Remove by actor
  def rem(actor: ActorRef): Unit = {
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

  override def receive: Receive = {
    case event@ MemberUp(member) =>
      ///log.error(s"event $event from ${sender()}")
      if (!isLocal(member.address)) {
        sendGetUsers(member.address)
      }

    case UnreachableMember(member) =>
      removeAllRemote(member.address)

    case ServerOnline =>
      publish(TOPIC_USERS, GetUsers)

    case GetUsers =>
      sender() ! ReceivedUsers(
        localUsers
          .map(actor => reverseUsers(actor) -> actor)
          .toSeq
      )

    case ReceivedUsers(remoteUsers) =>
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
//      println("=== LOCALUSERS " + localUsers.size)
//      println(localUsers)
//      println("=== REVERSEUSERS " + reverseUsers.size)
//      println(reverseUsers)
//      println("=== REMOTEUSERMAP " + remoteUsersMap.values.map(_.size).toSeq)
//      println(remoteUsersMap)
//      println("=== USERS " + users.size)
//      println(users)
      sender() ! UserInfo(USERS(localUsers.size, users.size))

    case RemoteEvent(event) =>
      if (isRemote()) {
        handleRemote(event)
      }

    case event =>
      //log.error(s"event $event from ${sender()}")
      handleLocal(event)
  }

  def handleLocal: Receive = {
    case SplitMe =>
      if (isLocal()) {
        publish(TOPIC_USERS, RemoteEvent(RemActors(localUsers.toSet)))
        users
          .filterNot {
            case (key, (name, actor)) =>
              localUsers.contains(actor)
          }
          .foreach {
            case (key, (name, actor)) =>
              self ! Rem(actor)
          }
      }

    case Add(connection, user, protocol) =>
      localAdd(connection, user, protocol)

    case Rem(userActor) =>
      rem(userActor)

    case BroadcastCommand(message) =>
      publish(TOPIC_USERS, RemoteEvent(BroadcastCommand(message)))
      localUsers ! UserError(message)

    case DisconnectCommand(user) =>
      rem(user)

    case _ =>
  }

  def handleRemote: Receive = {
    case Add(userActor, user, _) =>
      remoteAdd(userActor, user.name)

    case c @ RemActors(userActors) =>
      userActors.foreach(rem)

    case BroadcastCommand(message) =>
      //println(s"### Remote Broadcast $localUsers")
      localUsers ! UserError(message)
  }

  def getRealUser(user: User): User = {
    if (Config.Accounts.enableMultipleLogins) {
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
