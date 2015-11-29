package com.vilenet.users

import akka.actor.{Props, ActorRef}
import akka.io.Tcp.Event
import com.vilenet.channels.utils.RemoteEvent
import com.vilenet.coders.{UserToChannelCommand, UserCommand, Command}
import com.vilenet.servers.{ServerOnline, AddListener}
import com.vilenet.{Constants, ViLeNetComponent, ViLeNetActor}
import com.vilenet.channels._
import com.vilenet.Constants._
import com.vilenet.utils.RealKeyedCaseInsensitiveHashMap

import scala.collection.mutable

/**
 * Created by filip on 9/28/15.
 */
case class Add(connection: ActorRef, user: User, protocol: Protocol) extends Event
case class Rem(username: String) extends Event

case class WhisperTo(user: User, username: String, message: String) extends Event

object UsersActor extends ViLeNetComponent {
  def apply() = system.actorOf(Props(new UsersActor), VILE_NET_USERS_PATH)
}

trait Protocol
case object BinaryProtocol extends Protocol
case object TelnetProtocol extends Protocol

case object GetUsers
case class ReceivedUser(user: (String, ActorRef))
case class ReceivedUsers(users: RealKeyedCaseInsensitiveHashMap[ActorRef])
case class UserToChannelCommandAck(userActor: ActorRef, realUsername: String, command: UserToChannelCommand) extends Command

class UsersActor extends ViLeNetActor {

  var remoteUsersActors = mutable.HashSet[ActorRef]()

  val remoteUsersActor = (actor: ActorRef) =>
    system.actorSelection(s"akka.tcp://${actor.path.address.hostPort}/user/$VILE_NET_USERS_PATH")

  var users = RealKeyedCaseInsensitiveHashMap[ActorRef]()

  serverColumbus ! AddListener

  override def receive: Receive = {
    case ServerOnline(columbus) =>
      remoteUsersActor(columbus) ! GetUsers

    case GetUsers =>
      remoteUsersActors += sender()
      sender() ! ReceivedUsers(users)

    case ReceivedUser(remoteUser) =>
      users += remoteUser

    case ReceivedUsers(remoteUsers) =>
      //log.error(s"Received remote users: $remoteUsers")
      users ++= remoteUsers

    case command: UserToChannelCommand =>
      sender() ! users.getWithRealKey(command.toUsername)
        .fold[Command](UserError(Constants.USER_NOT_LOGGED_ON))(keyedUser => UserToChannelCommandAck(keyedUser._2, keyedUser._1, command))

    case command: UserCommand =>
      log.error(s"command sending $command")
      users.get(command.toUsername)
        .fold(sender() ! UserError(Constants.USER_NOT_LOGGED_ON))(x => {
        log.error(s"users $users")
        log.error(s"sending to $x from ${sender()}")
        x ! (sender(), command)
      })

    case RemoteEvent(event) =>
      handleRemote(event)

    case event =>
      log.error(s"event $event from ${sender()}")
      handleLocal(event)
  }

  def handleLocal: Receive = {
    case Add(connection, user, protocol) =>
      val userActor = context.actorOf(UserActor(connection, user, protocol))
      users += user.name -> userActor
      remoteUsersActors.foreach(_ ! RemoteEvent(Add(userActor, user, protocol)))
      sender() ! userActor

    case Rem(username) =>
      users -= username
      remoteUsersActors.foreach(_ ! RemoteEvent(Rem(username)))
  }

  def handleRemote: Receive = {
    case Add(userActor, user, _) =>
      users += user.name -> userActor

    case Rem(username) =>
      users -= username
  }
}
