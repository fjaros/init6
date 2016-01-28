package com.vilenet.users

import java.util.concurrent.TimeUnit

import akka.actor.{Address, Terminated, Props, ActorRef}
import akka.cluster.ClusterEvent.{UnreachableMember, MemberUp}
import akka.pattern.ask
import akka.util.Timeout
import com.vilenet.channels.utils.{RemoteMultiMap, LocalUsersSet}
import com.vilenet.coders.commands._
import com.vilenet.servers._
import com.vilenet.{ViLeNetClusterActor, Constants, ViLeNetComponent}
import com.vilenet.channels._
import com.vilenet.Constants._
import com.vilenet.utils.{FiniteArrayBuffer, RealKeyedCaseInsensitiveHashMap}

import scala.collection.mutable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success}

/**
 * Created by filip on 9/28/15.
 */
case class Add(connection: ActorRef, user: User, protocol: Protocol) extends Command
case class Rem(username: String) extends Command
case class RemActors(userActors: Set[ActorRef]) extends Command

case class WhisperTo(user: User, username: String, message: String)  extends Command

object UsersActor extends ViLeNetComponent {
  def apply() = system.actorOf(Props[UsersActor], VILE_NET_USERS_PATH)
}

trait Protocol extends Command
case object BinaryProtocol extends Protocol
case object TelnetProtocol extends Protocol
case object Chat1Protocol extends Protocol

case object GetUsers extends Command
case class ReceivedUser(user: (String, ActorRef)) extends Command
case class ReceivedUsers(users: Seq[(String, ActorRef)]) extends Command
case class UserToChannelCommandAck(userActor: ActorRef, realUsername: String, command: UserToChannelCommand) extends Command
case class UsersUserAdded(userActor: ActorRef, user: User) extends Command

class UsersActor extends ViLeNetClusterActor {

  var placeCounter = 1

  val remoteUsersActor = (address: Address) =>
    system.actorSelection(s"akka.tcp://${address.hostPort}/user/$VILE_NET_USERS_PATH")

  val users = RealKeyedCaseInsensitiveHashMap[ActorRef]()
  val reverseUsers = mutable.HashMap[ActorRef, String]()
  val localUsers = LocalUsersSet()
  val remoteUsersMap = RemoteMultiMap[Address, ActorRef]()

  val topMap = Map(
    "binary" -> FiniteArrayBuffer[User](),
    "chat" -> FiniteArrayBuffer[User](),
    "all" -> FiniteArrayBuffer[User]()
  )

  subscribe(TOPIC_ONLINE)
  subscribe(TOPIC_USERS)
  subscribe(TOPIC_SPLIT)

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

  override def receive: Receive = {
    case MemberUp(member) =>
      if (!isLocal(member.address)) {
        sendGetUsers(member.address)
      }

    case UnreachableMember(member) =>
      remoteUsersMap.get(member.address).fold()(unreachableActors => {
        unreachableActors.foreach(unreachableActor => {
          reverseUsers.get(unreachableActor).fold()(username => {
            rem(unreachableActor, username)
          })
        })
      })

    case ServerOnline =>
      publish(TOPIC_USERS, GetUsers)

    case GetUsers =>
      sender() ! ReceivedUsers(users.values.toSeq)

    case ReceivedUsers(remoteUsers) =>
      if (!isLocal()) {
        val address = sender().path.address
        remoteUsers
          .map(_._2)
          .foreach(context.watch)
        remoteUsersMap ++= address -> remoteUsers.map(_._2)
        remoteUsers.foreach(users += _)

        // yeah .. but each server gets this...
//        remoteUsers.foreach {
//          case (name, actor) =>
//            users.get(name).fold() {
//              case (currentName, currentActor) =>
//                currentActor ! KillSelf
//                rem(currentActor, currentName)
//            }
//            users += name -> actor
//        }
        reverseUsers ++=
          remoteUsers.map {
            case (name, actor) => actor -> name
          }
      }

    case command: UserToChannelCommand =>
      sender() ! users.get(command.toUsername)
        .fold[Command](UserError(Constants.USER_NOT_LOGGED_ON))(keyedUser => UserToChannelCommandAck(keyedUser._2, keyedUser._1, command))

    case command: UserCommand =>
      //log.error(s"command sending $command")
      users.get(command.toUsername)
        .fold(sender() ! UserError(Constants.USER_NOT_LOGGED_ON))(x => {
        ////log.error(s"users $users")
        //log.error(s"sending to $x from ${sender()}")
        x._2 ! (sender(), command)
      })

    case UsersCommand =>
      sender() ! UserInfo(USERS(localUsers.size, users.size))

    case TopCommand(which) =>
      val topList = topMap(which)
      sender() ! UserInfo(s"Showing the top ${topList.getInitialSize} $which connections:")
      topList
        .zipWithIndex
        .foreach {
          case (user, i) =>
            sender() ! UserInfo(s"${i + 1}. ${TOP_LIST(user.name, encodeClient(user.client))}")
        }

    case Terminated(actor) =>
      rem(actor, reverseUsers.getOrElse(actor, ""))

    case RemoteEvent(event) =>
      handleRemote(event)

    case event =>
      //log.error(s"event $event from ${sender()}")
      handleLocal(event)
  }

  def handleLocal: Receive = {
    case SplitMe =>
      if (isLocal()) {
        publish(TOPIC_USERS, RemoteEvent(RemActors(localUsers.toSet)))
        users
          .filterNot(tuple => localUsers.contains(tuple._2._2))
          .foreach(tuple => {
            val actor = tuple._2._2
            reverseUsers.get(actor).fold()(username => {
              self ! Rem(username)
            })
          })
      }

    case c@ Add(connection, user, protocol) =>
      //println(c)
      val newUser = getRealUser(user).copy(place = placeCounter)
      placeCounter += 1
      val userActor = context.actorOf(UserActor(connection, newUser, protocol))
//      context.watch(userActor)
      topMap(
        newUser.client match {
          case "CHAT" | "TAHC" => "chat"
          case _ => "binary"
        }
      ) += newUser
      topMap("all") += newUser
      users += newUser.name -> userActor
      reverseUsers += userActor -> newUser.name
      localUsers += userActor
      publish(TOPIC_USERS, RemoteEvent(Add(userActor, newUser, protocol)))
      sender() ! UsersUserAdded(userActor, newUser)

    case c @ Rem(username) =>
      rem(sender(), username)

    case BroadcastCommand(message) =>
      publish(TOPIC_USERS, RemoteEvent(BroadcastCommand(message)))

    case _ =>
  }

  def rem(userActor: ActorRef, username: String) = {
    localUsers -= userActor
    reverseUsers -= userActor
    users -= username
  }

  def handleRemote: Receive = {
    case Add(userActor, user, _) =>
      if (!isLocal()) {
        users += user.name -> userActor
        reverseUsers += userActor -> user.name
      }

    case c @ RemActors(userActors) =>
      if (!isLocal()) {
        //println(c)
        userActors.foreach(userActor => {
          reverseUsers.get(userActor).fold()(username => {
            users -= username
            reverseUsers -= userActor
          })
        })
      }

    case BroadcastCommand(message) =>
      //println(s"### Remote Broadcast $localUsers")
      localUsers ! UserError(message)
  }

  def getRealUser(user: User): User = {
    var number = 1
    var username = user.name
    while (users.contains(username)) {
      number = number + 1
      username = s"${user.name}#$number"
    }
    user.copy(name = username)
  }
}
