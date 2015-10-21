//package com.vilenet.channels
//
//import akka.actor.{Terminated, Props, ActorRef}
//import com.vilenet.ViLeNetActor
//import com.vilenet.coders.telnet.{EmoteMessage, ChatMessage, DesignateCommand}
//import com.vilenet.servers.RemoteEvent
//
//import scala.collection.mutable
//
///**
// * Created by filip on 9/20/15.
// */
//object OldChannel {
//  def apply(channelName: String) = Props(new OldChannel(channelName))
//}
//
//case class User(
//                // Static variables
//                name: String,
//                flags: Long = 0,
//                ping: Long = 0,
//                client: String = "CHAT",
//
//                // Changeable
//                channel: String = "Chat")
//
//case class ChannelUser(isLocal: Boolean, user: User)
//
//
//case class AddUser(userActor: ActorRef, user: User)
//case class RemUser(userActor: ActorRef)
//case class DesignateUser(fromUser: ActorRef, designatee: ActorRef)
//
//case class AddRemoteChannel(remoteChannel: ActorRef)
//case class RemRemoteChannel(remoteChannel: ActorRef)
//
//
//case class ChannelUsers(users: mutable.LinkedHashMap[ActorRef, User])
//
//object RemoteChannelsMultiMap {
//  def apply() = new RemoteChannelsMultiMap()
//}
//
//class RemoteChannelsMultiMap extends mutable.HashMap[ActorRef, mutable.Set[ActorRef]] with mutable.MultiMap[ActorRef, ActorRef] {
//  def +=(kv: (ActorRef, ActorRef)): this.type = addBinding(kv._1, kv._2)
//  def +=(key: ActorRef): this.type = +=(key -> mutable.Set[ActorRef]())
//  def !(message: Any): Unit = keys.foreach(_ ! RemoteEvent(message))
//}
//
//
//class OldChannel(channelName: String) extends ViLeNetActor {
//
//  var remoteChannels = RemoteChannelsMultiMap()
//
//  var users = mutable.LinkedHashMap[ActorRef, ChannelUser]()
//  var designatedUsers = mutable.HashMap[ActorRef, ActorRef]()
//
//  private implicit def resolveSenderToUser(user: ActorRef): User = users(user).user
//
//  override def receive: Receive = {
//    case RemoteEvent(event) =>
//      handle(event)
//    case event =>
//      handle(event)
//      remoteChannels ! event
//  }
//
//  def handle: Receive = {
//    case ChannelUsersRequest(actor) =>
//      log.error("CHANNEL USERS REQUEST")
//      actor ! RemoteEvent(ChannelUsersResponse(channelName, users))
//      remoteChannels += actor
//      context.watch(actor)
//
//    case RemRemoteChannel(actor) =>
//      remoteChannels -= actor
//      context.unwatch(actor)
//
//    case ChannelUsers(remoteChannelUsers) =>
//      log.error(s"### CHANNELUSERS $remoteChannelUsers")
//      remoteChannelUsers
//        .filter(remoteUser => !users.contains(remoteUser._1))
//        .foreach(remoteUser => {
//          users.keys.foreach(_ ! UserJoined(remoteUser._2))
//          users += remoteUser._1 -> ChannelUser(false, remoteUser._2)
//        })
//
//
//    case AddUser(actor, user) =>
//      val cpUser = user.copy(
//        flags = if (users.isEmpty) user.flags | 0x02 else user.flags,
//        channel = channelName
//      )
//      add(actor, cpUser)
//
//    case RemoteEvent(AddUser(actor, user)) =>
//      val cpUser = ChannelUser(false, user.copy(
//        flags = if (users.isEmpty) user.flags | 0x02 else user.flags,
//        channel = channelName
//      ))
//      users += actor-> cpUser
//
//    case RemUser(user) =>
//      log.error(s"### REMOVING USER $user")
//      rem(user)
//
//    case Terminated(actor) =>
//      if (remoteChannels.contains(actor)) {
//        remoteChannels(actor).foreach(rem)
//      } else {
//        rem(actor)
//        remoteChannels ! RemUser(actor)
//      }
//
//    case ChatMessage(fromUser, message) =>
//      println(s"### users $users")
//      users
//        .filter(_._1 != sender())
//        .foreach(_._1 ! UserTalked(sender(), message))
//    case EmoteMessage(fromUser, message) =>
//      users
//        .foreach(_._1 ! UserEmote(sender(), message))
//
//    case DesignateUser(fromUser, designatee) =>
//      designate(fromUser, designatee)
//    case _ =>
//  }
//
//  def add(actor: ActorRef, user: User) = {
//    log.error(s"AddUser $user ${actor.path}")
//    actor ! UserChannel(user, channelName)
//    users.keys.foreach(channelUser => channelUser ! UserJoined(user))
//    context.watch(actor)
//    users += actor-> ChannelUser(true, user)
//    users.values.foreach(channelUser => actor ! UserIn(channelUser))
//  }
//
//  def rem(actor: ActorRef) = {
//    log.error(s"RemUser $actor")
//    users.get(actor).fold(log.error(s"No REMUSER FOUND $actor $users"))(user => {
//      users -= actor
//      context.unwatch(actor)
//      if (users.isEmpty) {
//        channelsActor ! ChatEmptied(channelName)
//      } else {
//        users.foreach(_._1 ! UserLeft(user))
//        isOperator(user).fold()((unit) => {
//          users.foreach(_._1 ! UserFlags({
//            designatedUsers.get(actor).fold({
//              val nextKv = users.iterator
//                .takeWhile(_ != actor)
//                .dropWhile(_ == actor)
//                .next()
//
//              users += nextKv._1 -> nextKv._2.copy(flags = nextKv._2.flags | 0x02)
//              users(nextKv._1)
//            })(usr => {
//              users += usr -> usr.copy(flags = usr.flags | 0x02)
//              users(usr)
//            })
//          }))
//        })
//      }
//      designatedUsers -= actor
//    })
//  }
//
//  def designate(fromActor: ActorRef, designatee: ActorRef) = {
//    users.get(fromActor).fold()(user => {
//      fromActor !
//        isOperator(user).fold[ChatEvent](UserError("You are not a channel operator."))((unit) =>
//          users.get(designatee).fold[ChatEvent](UserError("Invalid user."))(designatedUser => {
//            designatedUsers += fromActor -> designatee
//            UserInfo(s"${designatedUser.name} is your new designated heir.")
//          })
//        )
//    })
//  }
//
//  private def isOperator(user: User) = if ((user.flags & 0x02) == 0x02) Some() else None
//}
