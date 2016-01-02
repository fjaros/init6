package com.vilenet.users

import akka.actor.{Address, Terminated, Props, ActorRef}
import akka.cluster.ClusterEvent.MemberUp
import com.vilenet.channels.utils.LocalUsersSet
import com.vilenet.coders.commands._
import com.vilenet.servers._
import com.vilenet.{ViLeNetClusterActor, Constants, ViLeNetComponent}
import com.vilenet.channels._
import com.vilenet.Constants._
import com.vilenet.utils.{FiniteArrayBuffer, RealKeyedCaseInsensitiveHashMap}

import scala.collection.mutable

/**
 * Created by filip on 9/28/15.
 */
case class Add(connection: ActorRef, user: User, protocol: Protocol) extends Command
case class Rem(username: String) extends Command
case class RemActors(userActors: mutable.Set[ActorRef]) extends Command

case class WhisperTo(user: User, username: String, message: String)  extends Command

object UsersActor extends ViLeNetComponent {
  def apply() = system.actorOf(Props[UsersActor], VILE_NET_USERS_PATH)
}

trait Protocol extends Command
case object BinaryProtocol extends Protocol
case object TelnetProtocol extends Protocol

case object GetUsers extends Command
case class ReceivedUser(user: (String, ActorRef)) extends Command
case class ReceivedUsers(users: RealKeyedCaseInsensitiveHashMap[ActorRef]) extends Command
case class UserToChannelCommandAck(userActor: ActorRef, realUsername: String, command: UserToChannelCommand) extends Command
case class UsersUserAdded(userActor: ActorRef, user: User) extends Command

class UsersActor extends ViLeNetClusterActor {

  var placeCounter = 1

  val remoteUsersActor = (address: Address) =>
    system.actorSelection(s"akka.tcp://${address.hostPort}/user/$VILE_NET_USERS_PATH")

  var users = RealKeyedCaseInsensitiveHashMap[ActorRef]()
  var localUsers = LocalUsersSet()
  var reverseUsers = mutable.HashMap[ActorRef, String]()

  var topMap = Map(
    "binary" -> FiniteArrayBuffer[User](),
    "chat" -> FiniteArrayBuffer[User](),
    "all" -> FiniteArrayBuffer[User]()
  )

  subscribe(TOPIC_ONLINE)
  subscribe(TOPIC_USERS)
  subscribe(TOPIC_SPLIT)

  override def receive: Receive = {
    case MemberUp(member) =>
      if (!isLocal(member.address)) {
        remoteUsersActor(member.address) ! GetUsers
      }
//      if (isLocal(member.address)) {
//        publish(TOPIC_USERS, GetUsers)
//      } else {
//        remoteUsersActor(member.address) ! GetUsers
//      }

    case ServerOnline =>
      publish(TOPIC_USERS, GetUsers)

    case GetUsers =>
      sender() ! ReceivedUsers(users)

    case ReceivedUser(remoteUser) =>
      context.watch(remoteUser._2)
      users += remoteUser
      reverseUsers += remoteUser._2 -> remoteUser._1

    case ReceivedUsers(remoteUsers) =>
      if (!isLocal()) {
        remoteUsers
          .values
          .map(_._2)
          .foreach(context.watch)
        users ++= remoteUsers
        reverseUsers ++= remoteUsers.map(tuple => tuple._2._2 -> tuple._1)
      }

    case command: UserToChannelCommand =>
      sender() ! users.get(command.toUsername)
        .fold[Command](UserError(Constants.USER_NOT_LOGGED_ON))(keyedUser => UserToChannelCommandAck(keyedUser._2, keyedUser._1, command))

    case command: UserCommand =>
      log.error(s"command sending $command")
      users.get(command.toUsername)
        .fold(sender() ! UserError(Constants.USER_NOT_LOGGED_ON))(x => {
        //log.error(s"users $users")
        log.error(s"sending to $x from ${sender()}")
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
      println(s"TERMINATED $actor $reverseUsers")
      reverseUsers.get(actor).fold()(username => {
        users.get(username).fold()(userActor => {
          context.unwatch(userActor._2)
          users -= username
          reverseUsers -= userActor._2
          localUsers -= userActor._2
        })
      })

    case RemoteEvent(event) =>
      handleRemote(event)

    case event =>
      log.error(s"event $event from ${sender()}")
      handleLocal(event)
  }

  def handleLocal: Receive = {
    case SplitMe =>
      if (isLocal()) {
        publish(TOPIC_USERS, RemoteEvent(RemActors(localUsers)))
        users
          .filterNot(tuple => localUsers.contains(tuple._2._2))
          .foreach(tuple => {
            val actor = tuple._2._2
            reverseUsers.get(actor).fold()(username => {
              self ! RemoteEvent(Rem(username))
            })
          })
      }

    case Add(connection, user, protocol) =>
      val newUser = getRealUser(user).copy(place = placeCounter)
      placeCounter += 1
      val userActor = context.actorOf(UserActor(connection, newUser, protocol))
      context.watch(userActor)
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

    case Rem(username) =>
      users.get(username).fold()(userActor => {
        context.unwatch(userActor._2)
        users -= username
        localUsers -= userActor._2
        reverseUsers -= userActor._2
        publish(TOPIC_USERS, RemoteEvent(Rem(username)))
      })

    case BroadcastCommand(message) =>
      publish(TOPIC_USERS, RemoteEvent(BroadcastCommand(message)))

    case _ =>
  }

  def handleRemote: Receive = {
    case Add(userActor, user, _) =>
      context.watch(userActor)
      users += user.name -> userActor
      reverseUsers += userActor -> user.name

    case Rem(username) =>
      users.get(username).fold()(userActor => {
        context.unwatch(userActor._2)
        users -= username
        reverseUsers -= userActor._2
      })

    case c @ RemActors(userActors) =>
      if (!isLocal()) {
        println(c)
        userActors.foreach(userActor => {
          reverseUsers.get(userActor).fold()(username => {
            users -= username
            reverseUsers -= userActor
          })
        })
      }

    case BroadcastCommand(message) =>
      println(s"### Remote Broadcast $localUsers")
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
