package com.vilenet.users

import akka.actor.{Terminated, Props, ActorRef}
import com.vilenet.coders.commands.{TopCommand, UserCommand, UserToChannelCommand, Command}
import com.vilenet.servers.{RemoteEvent, ServerOffline, ServerOnline, AddListener}
import com.vilenet.{Constants, ViLeNetComponent, ViLeNetActor}
import com.vilenet.channels._
import com.vilenet.Constants._
import com.vilenet.utils.{FiniteArrayBuffer, RealKeyedCaseInsensitiveHashMap}

import scala.collection.mutable

/**
 * Created by filip on 9/28/15.
 */
case class Add(connection: ActorRef, user: User, protocol: Protocol) extends Command
case class Rem(username: String) extends Command

case class WhisperTo(user: User, username: String, message: String)  extends Command

object UsersActor extends ViLeNetComponent {
  def apply() = system.actorOf(Props(new UsersActor), VILE_NET_USERS_PATH)
}

trait Protocol extends Command
case object BinaryProtocol extends Protocol
case object TelnetProtocol extends Protocol

case object GetUsers extends Command
case class ReceivedUser(user: (String, ActorRef)) extends Command
case class ReceivedUsers(users: RealKeyedCaseInsensitiveHashMap[ActorRef]) extends Command
case class UserToChannelCommandAck(userActor: ActorRef, realUsername: String, command: UserToChannelCommand) extends Command
case class UsersUserAdded(userActor: ActorRef, user: User) extends Command

class UsersActor extends ViLeNetActor {

  var placeCounter = 1
  var remoteUsersActors = mutable.HashSet[ActorRef]()

  val remoteUsersActor = (actor: ActorRef) =>
    system.actorSelection(s"akka.tcp://${actor.path.address.hostPort}/user/$VILE_NET_USERS_PATH")

  var users = RealKeyedCaseInsensitiveHashMap[ActorRef]()
  var reverseUsers = mutable.HashMap[ActorRef, String]()

  var topMap = Map(
    "binary" -> FiniteArrayBuffer[User](),
    "chat" -> FiniteArrayBuffer[User](),
    "all" -> FiniteArrayBuffer[User]()
  )

  serverColumbus ! AddListener

  override def receive: Receive = {
    case ServerOnline(columbus) =>
      remoteUsersActor(columbus) ! GetUsers

    case GetUsers =>
      remoteUsersActors += sender()
      sender() ! ReceivedUsers(users)

    case ReceivedUser(remoteUser) =>
      context.watch(remoteUser._2)
      users += remoteUser
      reverseUsers += remoteUser._2 -> remoteUser._1

    case ReceivedUsers(remoteUsers) =>
      println(s"${remoteUsers.realKeyMap}")
      remoteUsers
        .values
        .foreach(context.watch)
      //users ++= remoteUsers.map(tuple => remoteUsers.getWithRealKey(tuple._1).getOrElse(tuple))
      reverseUsers ++= remoteUsers.map(tuple => tuple._2 -> tuple._1)

    case command: UserToChannelCommand =>
      users.keys.map(users.getWithRealKey).foreach(println)

      sender() ! users.getWithRealKey(command.toUsername)
        .fold[Command](UserError(Constants.USER_NOT_LOGGED_ON))(keyedUser => UserToChannelCommandAck(keyedUser._2, keyedUser._1, command))

    case command: UserCommand =>
      log.error(s"command sending $command")
      users.get(command.toUsername)
        .fold(sender() ! UserError(Constants.USER_NOT_LOGGED_ON))(x => {
        //log.error(s"users $users")
        log.error(s"sending to $x from ${sender()}")
        x ! (sender(), command)
      })

    case TopCommand(which) =>
      val topList = topMap(which)
      sender() ! UserInfo(s"Showing the top ${topList.getInitialSize} $which connections:")
      for (i <- 1 to topList.size) {
        val user = topList(i - 1)
        sender() ! UserInfo(s"$i. ${TOP_LIST(user.name, encodeClient(user.client))}")
      }

    case Terminated(actor) =>
      println(s"TERMINATED $actor $reverseUsers")
      reverseUsers.get(actor).fold()(username => {
        users.get(username).fold()(userActor => {
          context.unwatch(userActor)
          users -= username
        })
      })

    case RemoteEvent(event) =>
      handleRemote(event)

    case event =>
      log.error(s"event $event from ${sender()}")
      handleLocal(event)
  }

  def handleLocal: Receive = {
    case ServerOffline(columbus) =>

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
      remoteUsersActors.foreach(_ ! RemoteEvent(Add(userActor, newUser, protocol)))
      sender() ! UsersUserAdded(userActor, newUser)

    case Rem(username) =>
      users.get(username).fold()(userActor => {
        context.unwatch(userActor)
        users -= username
        reverseUsers -= userActor
        remoteUsersActors.foreach(_ ! RemoteEvent(Rem(username)))
      })
  }

  def handleRemote: Receive = {
    case Add(userActor, user, _) =>
      context.watch(userActor)
      users += user.name -> userActor
      reverseUsers += userActor -> user.name

    case Rem(username) =>
      users.get(username).fold()(userActor => {
        context.unwatch(userActor)
        users -= username
        reverseUsers -= userActor
      })
  }

  def getRealUser(user: User): User = {
    var number = 1
    var username = user.name
    //println(s"$users $username $number")
    while (users.contains(username)) {
      number = number + 1
      username = s"${user.name}#$number"
    }
    user.copy(name = username)
  }
}
