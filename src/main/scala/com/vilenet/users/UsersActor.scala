package com.vilenet.users

import akka.actor.{Props, ActorRef}
import akka.io.Tcp.Event
import com.vilenet.servers.AddListener
import com.vilenet.{ViLeNetComponent, ViLeNetActor}
import com.vilenet.channels._
import com.vilenet.coders.telnet.UserCommand
import com.vilenet.Constants._
import com.vilenet.utils.CaseInsensitiveHashMap

/**
 * Created by filip on 9/28/15.
 */
case class Add(connection: ActorRef, user: User) extends Event
case class Rem(username: String) extends Event

case class WhisperTo(user: User, username: String, message: String) extends Event

object UsersActor extends ViLeNetComponent {
  def apply() = system.actorOf(Props(new UsersActor), VILE_NET_USERS_PATH)
}

case object GetUsers
case class ReceivedUser(user: (String, ActorRef))
case class ReceivedUsers(users: CaseInsensitiveHashMap[ActorRef])

class UsersActor extends ViLeNetActor {

  val remoteUsersActor = (actor: ActorRef) =>
    system.actorSelection(s"akka.tcp://${actor.path.address.hostPort}/user/$VILE_NET_USERS_PATH")

  var users = CaseInsensitiveHashMap[ActorRef]()

  serverColumbus ! AddListener

  override def receive: Receive = {
    case GetUsers =>
      sender() ! ReceivedUsers(users)

    case ReceivedUser(remoteUser) =>
      users += remoteUser

    case ReceivedUsers(remoteUsers) =>
      //log.error(s"Received remote users: $remoteUsers")
      users ++= remoteUsers

    case Add(connection, user) =>
      //log.error(s"Add $user")
      val userActor = context.actorOf(UserActor(connection, user))
      users += user.name -> userActor
//      if (remoteAddress.nonEmpty) {
//        context.actorSelection(s"akka.tcp://$VILE_NET@$remoteAddress/user/$VILE_NET_USERS_PATH") ! ReceivedUser(user.name -> userActor)
//      }
      sender() ! userActor
    case Rem(username) =>
      users.remove(username).fold()(context.stop)
    case command: UserCommand =>
      users.get(command.toUsername)
        .fold(sender() ! UserError("Not logged on"))(_ ! (sender(), command))

    case x => //log.error(s"COMMAND $x")
  }
}
