//package com.vilenet.channels
//
//import akka.actor.{Terminated, ActorRef, Props}
//import com.vilenet.ViLeNetActor
//import com.vilenet.coders._
//import com.vilenet.channels.utils.RemoteEvent
//import com.vilenet.users.{UserUpdated, UserToChannelCommandAck}
//import com.vilenet.utils.CaseInsensitiveFiniteHashSet
//
//import scala.collection.mutable
//
///**
// * Created by filip on 9/20/15.
// */
//object OldChannelActor {
//  // Channel Factory
//  def apply(name: String) = Props({
//    name.toLowerCase match {
//      case "the void" => new VoidedChannelActor("The Void")
//      case "vile" => new PublicChannelActor("ViLe")
//      case _ => new OldChannelActor(name)
//    }
//  })
//}
//
//case class User(
//                 // Static variables
//                 name: String,
//                 flags: Long = 0,
//                 ping: Long = 0,
//                 client: String = "CHAT",
//
//                 // Changeable
//                 channel: String = "Chat")
//
//case class ServerTerminated(columbus: ActorRef)
//case class ChannelUsersRequest(remoteChannelsActor: ActorRef)
//case class ChannelUsersResponse(name: String, allUsers: mutable.Map[ActorRef, User], remoteUsers: mutable.Set[ActorRef])
//case class ChannelUsersLoad(remoteChannelActor: ActorRef, allUsers: mutable.Map[ActorRef, User], remoteUsers: mutable.Set[ActorRef])
//
//case class AddUser(actor: ActorRef, user: User)
//case class AddLocalUser(actor: ActorRef, user: User)
//case class RemUser(actor: ActorRef)
//
//object LocalUsersSet {
//  def apply() = new LocalUsersSet()
//}
//
//class LocalUsersSet extends mutable.HashSet[ActorRef] {
//  def !(message: Any)(implicit sender: ActorRef): Unit = foreach(_ ! message)
//}
//
//object RemoteChannelsMultiMap {
//  def apply() = new RemoteChannelsMultiMap()
//}
//
//class RemoteChannelsMultiMap
//  extends mutable.HashMap[ActorRef, mutable.Set[ActorRef]] with mutable.MultiMap[ActorRef, ActorRef] {
//
//  private val columbusToChannelMap = mutable.Map[ActorRef, ActorRef]()
//
//  def +=(columbus: ActorRef, kv: (ActorRef, ActorRef)): this.type = {
//    columbusToChannelMap += columbus -> kv._1
//    +=(kv)
//  }
//
//  def -=(columbus: ActorRef, key: ActorRef): this.type = {
//    columbusToChannelMap -= columbus
//    -=(key)
//  }
//
//  def +=(kv: (ActorRef, ActorRef)): this.type = addBinding(kv._1, kv._2)
//  def +=(key: ActorRef): this.type = +=(key -> mutable.Set[ActorRef]())
//  def !(message: Any)(implicit sender: ActorRef): Unit = {
//    message match {
//      case Unit =>
//      case _ => keys.foreach(_ ! RemoteEvent(message))
//    }
//  }
//
//  def tell(message: Any, sender: ActorRef): Unit = {
//    this.!(message)(sender)
//  }
//
//  def getByColumbus(columbus: ActorRef): Option[mutable.Set[ActorRef]] = {
//    columbusToChannelMap.get(columbus).fold[Option[mutable.Set[ActorRef]]](None)(get)
//  }
//}
//
//class OldChannelActor(name: String, limit: Int = 200) extends ViLeNetActor {
//
//  type PartialToRemote = PartialFunction[Any, Any]
//
//  // Set of users in this channel on this server
//  var localUsers = LocalUsersSet()
//
//  // Map of Actor -> Set[User], actor key is remote server's copy of this channel.
//  var remoteUsers = RemoteChannelsMultiMap()
//
//  // Linked Map of actor -> user. Actor can be local or remote.
//  var users = mutable.LinkedHashMap[ActorRef, User]()
//
//  // Designate Users actor -> actor
//  var designatedActors = mutable.HashMap[ActorRef, ActorRef]()
//
//  // Banned users
//  var bannedUsers = CaseInsensitiveFiniteHashSet(200)
//
//
//  override def receive: Receive = {
//    case ChannelCreated(remoteChannelActor, _) =>
//      remoteUsers += remoteChannelActor
//      remoteChannelActor ! RemoteEvent(ChannelUsersLoad(self, users, localUsers))
//
//    case ChannelUsersRequest(remoteChannelsActor) =>
//      remoteChannelsActor ! ChannelUsersResponse(name, users, localUsers)
//
//    case ServerTerminated(columbus) =>
//      remoteUsers.getByColumbus(columbus)
//        .fold(log.info(s"Remote server terminated but not found as a users key $columbus"))(_.foreach(rem))
//
//    case Terminated(actor) =>
//      log.error(s"Terminated $actor")
//      rem(actor)
//      remoteUsers ! RemUser(actor)
//
//    case AddLocalUser(actor, user) =>
//      add(actor, user)
//      localUsers += actor
//
//    case RemoteEvent(event) =>
//      log.error(s"Channel $name sender: ${sender()} RemoteEvent($event)")
//      handleRemote(event)
//
//    case event =>
//      log.error(s"Channel $name $event")
//      val eventRemote = handleLocal(event)
//      log.error(s"Sending to remote $remoteUsers $eventRemote")
//      remoteUsers.tell(eventRemote, sender())
//  }
//
//  def handleLocal: PartialToRemote = {
//    case BlizzMe(user) =>
//      blizzMe(user)
//      (sender(), BlizzMe(user))
//
//    case AddUser(actor, user) =>
//      add(actor, user) match {
//        case value: User => AddUser(actor, value)
//        case _ =>
//      }
//
//    case RemUser(actor) =>
//      log.error(s"RemUser $actor")
//      rem(actor)
//      RemUser(actor)
//
//    case ChatMessage(user, message) =>
//      onChatMessage(user, message)
//      ChatMessage(user, message)
//
//    case EmoteMessage(user, message) =>
//      onEmoteMessage(user, message)
//      EmoteMessage(user, message)
//
//    case InfoMessage(message) =>
//      onInfoMessage(message)
//      InfoMessage(message)
//
//    case userToChannel: UserToChannelCommandAck =>
//     // userToChannel.command match {
////        case BanCommand(fromUser, _) =>
//  //        ban(sender(), userToChannel.userActor, userToChannel.realUsername)
//    //      userToChannel
////        case UnbanCommand(fromUser, _) =>
////          unban(sender(), userToChannel.realUsername)
////          userToChannel
////        case KickCommand(fromUser, _) =>
////          kick(sender(), userToChannel.userActor)
////        case DesignateCommand(fromUser, _) =>
////          designate(sender(), userToChannel.userActor)
////          userToChannel
//      //}
//      //userToChannel
//  }
//
//  def handleRemote: Receive = {
//    case (actor: ActorRef, BlizzMe(user)) =>
//      val blizzedUser = user.copy(flags = user.flags | 0x01)
//      users.put(actor, blizzedUser)
//      localUsers ! UserFlags(blizzedUser)
//
//    case ChatMessage(user, message) =>
//      onRemoteChatMessage(user, message)
//
//    case EmoteMessage(user, message) =>
//      onEmoteMessage(user, message)
//
//    case InfoMessage(message) =>
//      onInfoMessage(message)
//
//    case ChannelUsersLoad(remoteChannelActor, allUsers, remoteUsersLoad) =>
//      log.error(s"ChannelUsersLoad $remoteChannelActor $allUsers $remoteUsersLoad")
//      onChannelUsersLoad(remoteChannelActor, allUsers, remoteUsersLoad)
//      remoteUsers += remoteChannelActor -> remoteUsersLoad
//
//
//    case AddUser(actor, user) =>
//      log.error(s"Remote AddUser sender: ${sender()} actor: $actor user: $user")
//      remoteAdd(actor, user)
//
//    case RemUser(actor) =>
//      log.error(s"Remote RemUser $actor")
//      remoteRem(actor)
//
//    case userToChannel: UserToChannelCommandAck =>
////      userToChannel.command match {
////        case BanCommand(fromUser, _) =>
////          ban(sender(), userToChannel.userActor, userToChannel.realUsername)
////        case UnbanCommand(fromUser, _) =>
////          unban(sender(), userToChannel.realUsername)
////        case DesignateCommand(fromUser, _) =>
////          designate(sender(), userToChannel.userActor)
////      }
//
//    case x =>
//      log.error(s"Unhandled remote command $x")
//  }
//
//  def onChannelUsersLoad(remoteChannelActor: ActorRef, allUsers: mutable.Map[ActorRef, User], remoteUsersLoad: mutable.Set[ActorRef]) = {
//    allUsers
//      .filterNot(tuple => users.contains(tuple._1))
//      .foreach(tuple => {
//        log.error(s"Adding User From Load $tuple")
//        users += tuple
//        localUsers ! UserIn(tuple._2)
//      })
//  }
//
//  def onChatMessage(user: User, message: String) = {
//    localUsers
//      .filterNot(_ == sender())
//      .foreach(_ ! UserTalked(user, message))
//  }
//
//  def onInfoMessage(message: String) = {
//    localUsers ! UserInfo(message)
//  }
//
//  def onRemoteChatMessage(user: User, message: String) = {
//    localUsers ! UserTalked(user, message)
//  }
//
//  def onEmoteMessage(user: User, message: String) = {
//    localUsers ! UserEmote(user, message)
//  }
//
//  def add(actor: ActorRef, user: User) = {
//    if (users.size >= limit) {
//      actor ! UserError("Channel is full.")
//    } else if (bannedUsers(user.name)) {
//      actor ! UserError("You are banned from that channel.")
//    } else {
//
//      val updatedUser = user.copy(
//        flags =
//          if (users.isEmpty) {
//            user.flags | 0x02
//          } else {
//            user.flags & ~0x02
//          },
//        channel = name
//      )
//
//      val userJoined = UserJoined(updatedUser)
//      localUsers
//        .foreach(_ ! userJoined)
//
//      users += actor -> updatedUser
//      localUsers += actor
//      context.watch(actor)
//
//      actor ! UserChannel(updatedUser, name, self)
//
//      users
//        .values
//        .foreach(actor ! UserIn(_))
//
//      updatedUser
//    }
//  }
//
//  def remoteAdd(actor: ActorRef, user: User) = {
//    if (!users.contains(actor)) {
//      users += actor -> user
//      remoteUsers.get(sender()).fold(log.error(s"Remote user added but no remote channel actor found ${sender()}"))(_ += actor)
//      val userJoined = UserJoined(user)
//      localUsers
//        .foreach(_ ! userJoined)
//    }
//  }
//
//  def rem(actor: ActorRef) = {
//    log.error(s"rem rem $actor")
//    users.get(actor).fold()(user => {
//      context.unwatch(actor)
//      users -= actor
//      localUsers -= actor
//      localUsers ! UserLeft(user)
//
//      if (users.isEmpty) {
//        channelsActor ! ChatEmptied(name)
//      } else {
//        checkDesignatees(actor, user)
//      }
//    })
//  }
//
//  def remoteRem(actor: ActorRef) = {
//    if (users.contains(actor)) {
//      val user = users(actor)
//      users -= actor
//      remoteUsers.get(sender()).fold(log.error(s"Remote user removed but no remote channel actor found ${sender()}"))(_ -= actor)
//      val userLeft = UserLeft(user)
//      localUsers
//        .foreach(_ ! userLeft)
//
//      if (users.isEmpty) {
//        channelsActor ! ChatEmptied(name)
//      } else {
//        checkDesignatees(actor, user)
//      }
//    }
//  }
//
//  def kick(fromActor: ActorRef, kicked: ActorRef) = {
//    users.get(fromActor).fold()(user => {
//      if (isOperator(user)) {
//        users.get(kicked).fold(fromActor ! UserError("Invalid user."))(kickedUser => {
//          self ! InfoMessage(s"${kickedUser.name} was kicked out of the channel by ${user.name}.")
//         // kicked ! KickCommand(kickedUser.name)
//        })
//      } else {
//        fromActor ! UserError("You are not a channel operator.")
//      }
//    })
//  }
//
//  def ban(fromActor: ActorRef, bannedActor: ActorRef, bannedUsername: String) = {
//    // At this point we can assume banned is logged on
//    users.get(fromActor).fold()(user => {
//      if (isOperator(user)) {
//        bannedUsers += bannedUsername
//        users.get(bannedActor).fold({
//          bannedUsers += bannedUsername
//          localUsers ! UserInfo(s"$bannedUsername was banned by ${user.name}.")
//        })(bannedUser => {
//          if (isOperator(bannedUser)) {
//            fromActor ! UserError("You can't ban a channel operator.")
//          } else {
//            bannedUsers += bannedUsername
//          //  bannedActor ! BanCommand(bannedUser.name)
//            localUsers ! UserInfo(s"$bannedUsername was banned by ${user.name}.")
//          }
//        })
//      }
//    })
//  }
//
//  def unban(fromActor: ActorRef, unbannedUsername: String) = {
//    users.get(fromActor).fold()(user => {
//      if (isOperator(user)) {
//        if (bannedUsers(unbannedUsername)) {
//          bannedUsers -= unbannedUsername
//          localUsers ! UserInfo(s"$unbannedUsername was unbanned by ${user.name}.")
//        } else {
//          fromActor ! UserError("That user is not banned.")
//        }
//      }
//    })
//  }
//
//  def blizzMe(user: User) = {
//    val blizzedUser = user.copy(flags = user.flags | 0x01)
//
//    users.put(sender(), blizzedUser)
//    sender() ! UserUpdated(blizzedUser)
//    localUsers ! UserFlags(blizzedUser)
//  }
//
//  def designate(fromActor: ActorRef, designatee: ActorRef) = {
//    users.get(fromActor).fold()(user => {
//      fromActor !
//        (if (isOperator(user)) {
//          users.get(designatee).fold[ChatEvent](UserError("Invalid user."))(designatedUser => {
//            designatedActors += fromActor -> designatee
//            UserInfo(s"${designatedUser.name} is your new designated heir.")
//          })
//        } else {
//          UserError("You are not a channel operator.")
//        })
//    })
//  }
//
//  def checkDesignatees(forActor: ActorRef, actorUser: User) = {
//    // O(n) alert - check if there is another op already and if so then ignore designatee.
//    if (isOperator(actorUser) && !existsOperator) {
//      val designateeActor = designatedActors.getOrElse(forActor, users.head._1)
//      val designatedUser = users(designateeActor)
//
//      val oppedUser = opUser(designatedUser)
//      users.put(designateeActor, oppedUser)
//      designatedActors -= forActor
//      designateeActor ! UserUpdated(oppedUser)
//      localUsers ! UserFlags(oppedUser)
//    }
//  }
//
//  def existsOperator: Boolean = {
//    users
//      .values
//      .foreach(user => {
//        if (isOperator(user)) {
//          return true
//        }
//      })
//    false
//  }
//
//  val BLIZZ_FLAGS = 0x01
//  val OP_FLAGS = 0x02
//
//  def opUser(user: User) = user.copy(flags = user.flags | OP_FLAGS)
//  def deOpUser(user: User) = user.copy(flags = user.flags & ~OP_FLAGS)
//
//  def isOperator(user: User) = (user.flags & OP_FLAGS) == OP_FLAGS || (user.flags & BLIZZ_FLAGS) == BLIZZ_FLAGS
//}
