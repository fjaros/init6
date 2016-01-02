package com.vilenet.channels

import akka.actor.{Address, ActorRef, Props}
import akka.cluster.ClusterEvent.MemberUp
import com.vilenet.Constants._
import com.vilenet.coders.commands.{WhoCommandToChannel, WhoCommand, ChannelsCommand, Command}
import com.vilenet.{ViLeNetClusterActor, ViLeNetComponent}
import com.vilenet.servers._
import com.vilenet.utils.CaseInsensitiveHashMap

/**
 * Created by filip on 9/20/15.
 */
object ChannelsActor extends ViLeNetComponent {
  def apply() = system.actorOf(Props[ChannelsActor], VILE_NET_CHANNELS_PATH)
}

case object GetChannels extends Command
case class ChannelCreated(actor: ActorRef, name: String) extends Command
case class GetChannelUsers(remoteActor: ActorRef) extends Command
case class ReceivedChannel(channel: (String, ActorRef)) extends Command
case class UserAdded(actor: ActorRef, channel: String) extends Command


class ChannelsActor extends ViLeNetClusterActor {

  val remoteChannelsActor = (address: Address) =>
    system.actorSelection(s"akka.tcp://${address.hostPort}/user/$VILE_NET_CHANNELS_PATH")

  var channels = CaseInsensitiveHashMap[ActorRef]()

  subscribe(TOPIC_ONLINE)
  subscribe(TOPIC_CHANNELS)
  subscribe(TOPIC_SPLIT)


  override def receive: Receive = {
    case c@ MemberUp(member) =>
      if (!isLocal(member.address)) {
        remoteChannelsActor(member.address) ! GetChannels
      }

    case ServerOnline =>
      subscribe(TOPIC_CHANNEL)
      println("ServerOnline?")
      publish(TOPIC_CHANNEL, GetChannels)

    case SplitMe =>
      unsubscribe(TOPIC_CHANNELS)

    case GetChannels =>
      log.error(s"GetChannels sender ${sender()} ${isLocal()} $channels")
      if (!isLocal()) {
        channels
          .values
          .foreach(_ ! ChannelUsersRequest(sender()))
      }

    case ChannelCreated(actor, name) =>
      log.error(s"ChannelCreated $actor $name")
      if (!isLocal() &&
        name != "The Void" // haaackk:'(
      ) {
        getOrCreate(name) ! ChannelCreated(actor, name)
      }

    case ChannelUsersResponse(name, allUsers, remoteUsers) =>
      log.error(s"ChannelUsersResponse $name $allUsers $remoteUsers")
      getOrCreate(name) ! RemoteEvent(ChannelUsersLoad(sender(), allUsers, remoteUsers))

    case UserSwitchedChat(actor, user, channel) =>
      log.error(s"$user switched chat")

      getOrCreate(channel) ! AddUser(actor, user)

    case UserAdded(actor, channel) =>
      channels.get(channel).fold(log.error(s"Channel $channel not found"))(_ ! RemUser(actor))

    case ChatEmptied(channel) =>
      val lowerChannel = channel.toLowerCase
      channels -= lowerChannel

    case ChannelsCommand =>
      sender() ! UserInfo(CHANNEL_LIST(channels.size))
      channels
        .values
        .foreach(_ ! ChannelsCommand(sender()))

    case WhoCommand(user, channel) =>
      channels.get(channel).fold(sender() ! UserErrorArray(CHANNEL_NOT_EXIST))(_ ! WhoCommandToChannel(sender(), user))
  }
  
  def getOrCreate(name: String) = {
    channels.getOrElse(name, {
      val channelActor = context.actorOf(ChannelActor(name).withDispatcher(CHANNEL_DISPATCHER))
      channels += name -> channelActor
      publish(TOPIC_CHANNELS, ChannelCreated(channelActor, name))
      channelActor
    })
  }
}
