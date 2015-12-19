package com.vilenet.channels

import akka.actor.{ActorRef, Props}
import com.vilenet.Constants._
import com.vilenet.coders.commands.{WhoCommandToChannel, WhoCommand, ChannelsCommand, Command}
import com.vilenet.{ViLeNetComponent, ViLeNetActor}
import com.vilenet.servers._
import com.vilenet.utils.CaseInsensitiveHashMap

import scala.collection.mutable

/**
 * Created by filip on 9/20/15.
 */
object ChannelsActor extends ViLeNetComponent {
  def apply() = system.actorOf(Props(new ChannelsActor), VILE_NET_CHANNELS_PATH)
}

case class GetChannels(columbus: ActorRef) extends Command
case class ChannelCreated(actor: ActorRef, name: String) extends Command
case class GetChannelUsers(remoteActor: ActorRef) extends Command
case class ReceivedChannel(channel: (String, ActorRef)) extends Command
case class UserAdded(actor: ActorRef, channel: String) extends Command



class ChannelsActor extends ViLeNetActor {

  var remoteChannelsActors = mutable.HashSet[ActorRef]()

  val remoteChannelsActor = (actor: ActorRef) =>
    system.actorSelection(s"akka.tcp://${actor.path.address.hostPort}/user/$VILE_NET_CHANNELS_PATH")

  var channels = CaseInsensitiveHashMap[ActorRef]()

  serverColumbus ! AddListener


  override def receive: Receive = {
    case ServerOnline(columbus) =>
      log.error(s"GetChannelsActor: $columbus")
      remoteChannelsActor(columbus) ! GetChannels(columbus)

    case ServerOffline(columbus) =>
      log.error(s"ServerOffline: $columbus")
      channels
        .values
        .foreach(_ ! ServerOffline(columbus))

    case SplitMe =>
      channels
        .values
        .foreach(_ ! SplitMe)

    case GetChannels(columbus) =>
      log.error(s"GetChannels sender ${sender()} $channels")
      remoteChannelsActors += sender()
      channels
        .values
        .foreach(_ ! ChannelUsersRequest(sender()))

    case ChannelCreated(actor, name) =>
      log.error(s"ChannelCreated $actor $name")
      getOrCreate(name) ! ChannelCreated(actor, name)

    case ChannelUsersResponse(name, allUsers, remoteUsers) =>
      log.error(s"ChannelUsersResponse $name $allUsers $remoteUsers")
      getOrCreate(name) ! RemoteEvent(ChannelUsersLoad(sender(), allUsers, remoteUsers))

    case UserSwitchedChat(actor, user, channel) =>
      log.error(s"$user switched chat $remoteChannelsActors")

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
      remoteChannelsActors.foreach(_ ! ChannelCreated(channelActor, name))
      channelActor
    })
  }
}
