package com.vilenet.channels

import akka.actor.{ActorRef, Props}
import com.vilenet.Constants._
import com.vilenet.{ViLeNetComponent, ViLeNetActor}
import com.vilenet.servers.{RemoteEvent, ServerOnline, AddListener}
import com.vilenet.utils.CaseInsensitiveHashMap

import scala.collection.mutable

/**
 * Created by filip on 9/20/15.
 */
object ChannelsActor extends ViLeNetComponent {
  def apply() = system.actorOf(Props(new ChannelsActor), VILE_NET_CHANNELS_PATH)
}

case object GetChannels
case class ChannelCreated(actor: ActorRef, name: String)
case class GetChannelUsers(remoteActor: ActorRef)
case class ReceivedChannel(channel: (String, ActorRef))



class ChannelsActor extends ViLeNetActor {

  var remoteChannelsActors = mutable.HashSet[ActorRef]()

  val remoteChannelsActor = (actor: ActorRef) =>
    system.actorSelection(s"akka.tcp://${actor.path.address.hostPort}/user/$VILE_NET_CHANNELS_PATH")

  var channels = CaseInsensitiveHashMap[ActorRef]()

  serverColumbus ! AddListener


  override def receive: Receive = {
    case ServerOnline(columbus) =>
      log.error(s"GetChannelsActor: $columbus")
      remoteChannelsActor(columbus) ! GetChannels

    case GetChannels =>
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

      channels.get(user.channel).fold()(_ ! RemUser(actor))
      
      getOrCreate(channel) ! AddUser(actor, user)
        
      
    case ChatEmptied(channel) =>
      val lowerChannel = channel.toLowerCase
      context.stop(channels(lowerChannel))
      channels -= lowerChannel

  }
  
  def getOrCreate(name: String) = {
    channels.getOrElse(name, {
      val channelActor = context.actorOf(ChannelActor(name).withDispatcher("channel-dispatcher"))
      channels += name -> channelActor
      remoteChannelsActors.foreach(_ ! ChannelCreated(channelActor, name))
      channelActor
    })
  }
}
