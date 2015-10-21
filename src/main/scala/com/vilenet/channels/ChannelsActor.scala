package com.vilenet.channels

import akka.actor.{ActorRef, Props}
import com.vilenet.Constants._
import com.vilenet.{ViLeNetComponent, ViLeNetActor}
import com.vilenet.coders.telnet.ChannelCommand
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
case class GetChannelUsers(remoteActor: ActorRef)
case class ReceivedChannel(channel: (String, ActorRef))



class ChannelsActor extends ViLeNetActor {

  var remoteChannelsActors = mutable.HashSet[ActorRef]()

  val remoteChannelsActor = (actor: ActorRef) =>
    system.actorSelection(s"akka.tcp://${actor.path.address.hostPort}/user/$VILE_NET_CHANNELS_PATH")

  var channels = CaseInsensitiveHashMap[ActorRef]()

  serverColumbus ! AddListener


  override def receive: Receive = {
    case ServerOnline(actor) =>
      log.error(s"GetChannelsActor: $actor")
      remoteChannelsActor(actor) ! GetChannels

    case GetChannels =>
      channels
        .values
        .foreach(_ ! ChannelUsersRequest(sender()))

    case ChannelUsersResponse(name, allUsers, remoteUsers) =>
      channels.get(name).getOrElse({
        val channelActor = context.actorOf(ChannelActor(name))
        channels += name -> channelActor
        channelActor
      }) ! RemoteEvent(ChannelUsersLoad(sender(), allUsers, remoteUsers))

    case RemoteEvent(UserSwitchedChat(actor, user, channel)) =>
      log.error(s"$user switched chat $remoteChannelsActors")

      channels.get(channel).getOrElse({
        val channelActor = context.actorOf(ChannelActor(channel))
        channels += channel -> channelActor
        channelActor
      }) ! RemoteEvent(AddUser(actor, user))

    case UserSwitchedChat(actor, user, channel) =>
      log.error(s"$user switched chat $remoteChannelsActors")

      channels.get(user.channel).fold()(_ ! RemUser(actor))

      channels.get(channel).fold({
        val channelActor = context.actorOf(ChannelActor(channel))
        channels += channel -> channelActor
        channelActor ! AddLocalUser(actor, user)
        remoteChannelsActors.foreach(_ ! RemoteEvent(UserSwitchedChat(actor, user, channel)))
      })(_ ! AddUser(actor, user))


    case ChatEmptied(channel) =>
      val lowerChannel = channel.toLowerCase
      context.stop(channels(lowerChannel))
      channels -= lowerChannel

  }
}
