package com.vilenet.channels

import akka.actor.{Terminated, ActorRef, Props}
import com.vilenet.ViLeNetActor
import com.vilenet.coders.telnet.{DesignateCommand, EmoteMessage, ChatMessage}
import com.vilenet.servers.RemoteEvent
import com.vilenet.users.{UserUpdated, UserToChannelCommandAck}

import scala.collection.mutable

/**
 * Created by filip on 9/20/15.
 */
object ChannelActor {
  def apply(name: String) = Props(new ChannelActor(name))
}

case class User(
                 // Static variables
                 name: String,
                 flags: Long = 0,
                 ping: Long = 0,
                 client: String = "CHAT",

                 // Changeable
                 channel: String = "Chat")

case class ServerTerminated(columbus: ActorRef)
case class ChannelUsersRequest(remoteChannelsActor: ActorRef)
case class ChannelUsersResponse(name: String, allUsers: mutable.Map[ActorRef, User], remoteUsers: mutable.Set[ActorRef])
case class ChannelUsersLoad(remoteChannelActor: ActorRef, allUsers: mutable.Map[ActorRef, User], remoteUsers: mutable.Set[ActorRef])

case class AddUser(actor: ActorRef, user: User)
case class AddLocalUser(actor: ActorRef, user: User)
case class RemUser(actor: ActorRef)

object LocalUsersSet {
  def apply() = new LocalUsersSet()
}

class LocalUsersSet extends mutable.HashSet[ActorRef] {
  def !(message: Any): Unit = foreach(_ ! message)
}

object RemoteChannelsMultiMap {
  def apply() = new RemoteChannelsMultiMap()
}

class RemoteChannelsMultiMap
  extends mutable.HashMap[ActorRef, mutable.Set[ActorRef]] with mutable.MultiMap[ActorRef, ActorRef] {

  private val columbusToChannelMap = mutable.Map[ActorRef, ActorRef]()

  def +=(columbus: ActorRef, kv: (ActorRef, ActorRef)): this.type = {
    columbusToChannelMap += columbus -> kv._1
    +=(kv)
  }

  def -=(columbus: ActorRef, key: ActorRef): this.type = {
    columbusToChannelMap -= columbus
    -=(key)
  }

  def +=(kv: (ActorRef, ActorRef)): this.type = addBinding(kv._1, kv._2)
  def +=(key: ActorRef): this.type = +=(key -> mutable.Set[ActorRef]())
  def !(message: Any)(implicit sender: ActorRef): Unit = keys.foreach(_ ! RemoteEvent(message))

  def getByColumbus(columbus: ActorRef): Option[mutable.Set[ActorRef]] = {
    columbusToChannelMap.get(columbus).fold[Option[mutable.Set[ActorRef]]](None)(get)
  }
}

class ChannelActor(name: String) extends ViLeNetActor {

  type PartialToRemote = PartialFunction[Any, Any]

  // Set of users in this channel on this server
  var localUsers = LocalUsersSet()

  // Map of Actor -> Set[User], actor key is remote server's copy of this channel.
  var remoteUsers = RemoteChannelsMultiMap()

  // Linked Map of actor -> user. Actor can be local or remote.
  var users = mutable.LinkedHashMap[ActorRef, User]()

  // Designate Users actor -> actor
  var designatedActors = mutable.HashMap[ActorRef, ActorRef]()


  override def receive: Receive = {
    case ChannelCreated(remoteChannelActor, _) =>
      remoteUsers += remoteChannelActor
      remoteChannelActor ! RemoteEvent(ChannelUsersLoad(self, users, localUsers))

    case ChannelUsersRequest(remoteChannelsActor) =>
      remoteChannelsActor ! ChannelUsersResponse(name, users, localUsers)

    case ServerTerminated(columbus) =>
      remoteUsers.getByColumbus(columbus)
        .fold(log.info(s"Remote server terminated but not found as a users key $columbus"))(_.foreach(rem))

    case Terminated(actor) =>
      log.error(s"Terminated $actor")
      rem(actor)
      remoteUsers ! RemUser(actor)

    case AddLocalUser(actor, user) =>
      add(actor, user)
      localUsers += actor

    case RemoteEvent(event) =>
      log.error(s"Channel $name sender: ${sender()} RemoteEvent($event)")
      handleRemote(event)

    case event =>
      log.error(s"Channel $name $event")
      val eventRemote = handleLocal(event)
      log.error(s"Sending to remote $remoteUsers $eventRemote")
      remoteUsers ! eventRemote
  }

  def handleLocal: PartialToRemote = {
    case AddUser(actor, user) =>
      val updatedUser = user.copy(
        flags =
          if (users.isEmpty) {
            user.flags | 0x02
          } else {
            user.flags & ~0x02
          },
        channel = name
      )
      add(actor, updatedUser)
      AddUser(actor, updatedUser)

    case RemUser(actor) =>
      log.error(s"RemUser $actor")
      rem(actor)
      RemUser(actor)

    case ChatMessage(user, message) =>
      localUsers
        .filterNot(_ == sender())
        .foreach(_ ! UserTalked(user, message))
      ChatMessage(user, message)

    case EmoteMessage(user, message) =>
      localUsers
        .foreach(_ ! UserEmote(user, message))
      EmoteMessage(user, message)

    case userToChannel: UserToChannelCommandAck =>
      userToChannel.command match {
        case DesignateCommand(fromUser, toUsername) =>
          designate(sender(), userToChannel.userActor)
      }
      userToChannel
  }

  def handleRemote: Receive = {
    case ChatMessage(user, message) =>
      localUsers ! UserTalked(user, message)

    case EmoteMessage(user, message) =>
      localUsers ! UserEmote(user, message)

    case ChannelUsersLoad(remoteChannelActor, allUsers, remoteUsersLoad) =>
      log.error(s"ChannelUsersLoad $remoteChannelActor $allUsers $remoteUsersLoad")
      allUsers
        .filterNot(tuple => users.contains(tuple._1))
        .foreach(tuple => {
          log.error(s"Adding User From Load $tuple")
          users += tuple
          localUsers ! UserIn(tuple._2)
        })
      remoteUsers += remoteChannelActor -> remoteUsersLoad


    case AddUser(actor, user) =>
      log.error(s"Remote AddUser sender: ${sender()} actor: $actor user: $user")
      if (!users.contains(actor)) {
        users += actor -> user
        remoteUsers.get(sender()).fold(log.error(s"Remote user added but no remote channel actor found ${sender()}"))(_ += actor)
        val userJoined = UserJoined(user)
        localUsers
          .foreach(_ ! userJoined)
      }

    case RemUser(actor) =>
      log.error(s"Remote RemUser $actor")
      if (users.contains(actor)) {
        val user = users(actor)
        users -= actor
        remoteUsers.get(sender()).fold(log.error(s"Remote user removed but no remote channel actor found ${sender()}"))(_ -= actor)
        val userLeft = UserLeft(user)
        localUsers
          .foreach(_ ! userLeft)

        if (users.isEmpty) {
          channelsActor ! ChatEmptied(name)
        } else {
          checkDesignatees(actor, user)
        }
      }
    case x =>
      log.error(s"Unhandled remote command $x")
  }

  def add(actor: ActorRef, user: User) = {
    val userJoined = UserJoined(user)
    localUsers
      .foreach(_ ! userJoined)

    users += actor -> user
    localUsers += actor
    context.watch(actor)

    actor ! UserChannel(user, name, self)

    users
      .values
      .foreach(actor ! UserIn(_))
  }

  def rem(actor: ActorRef) = {
    log.error(s"rem rem $actor")
    users.get(actor).fold()(user => {
      context.unwatch(actor)
      users -= actor
      localUsers -= actor
      localUsers ! UserLeft(user)

      if (users.isEmpty) {
        channelsActor ! ChatEmptied(name)
      } else {
        checkDesignatees(actor, user)
      }
    })
  }

  def designate(fromActor: ActorRef, designatee: ActorRef) = {
    users.get(fromActor).fold()(user => {
      fromActor !
        (if (isOperator(user)) {
          users.get(designatee).fold[ChatEvent](UserError("Invalid user."))(designatedUser => {
            designatedActors += fromActor -> designatee
            UserInfo(s"${designatedUser.name} is your new designated heir.")
          })
        } else {
          UserError("You are not a channel operator.")
        })
    })
  }

  def checkDesignatees(forActor: ActorRef, actorUser: User) = {
    if (isOperator(actorUser)) {
      val designateeActor = designatedActors.getOrElse(forActor, users.head._1)
      val designatedUser = users(designateeActor)

      val oppedUser = opUser(designatedUser)
      users.put(designateeActor, oppedUser)
      designatedActors -= forActor
      designateeActor ! UserUpdated(oppedUser)
      localUsers ! UserFlags(oppedUser)
    }
  }

  val OP_FLAGS = 0x02

  def opUser(user: User) = user.copy(flags = user.flags | OP_FLAGS)
  def deOpUser(user: User) = user.copy(flags = user.flags & ~OP_FLAGS)

  def isOperator(user: User) = (user.flags & OP_FLAGS) == OP_FLAGS
}
