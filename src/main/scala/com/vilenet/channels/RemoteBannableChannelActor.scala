package com.vilenet.channels

import akka.actor.ActorRef
import com.vilenet.coders.commands.{UnbanCommand, BanCommand}
import com.vilenet.utils.CaseInsensitiveFiniteHashSet

/**
  * Created by filip on 11/26/15.
  */
trait RemoteBannableChannelActor extends RemoteChannelActor {

  // Banned users
  var bannedUsers = CaseInsensitiveFiniteHashSet(limit)

  override def receiveRemoteEvent = ({
    case BanCommand(banned, message) =>
      println(s"Got banned Remote $banned")
      bannedUsers += banned
    case UnbanCommand(unbanned) => bannedUsers -= unbanned
  }: Receive)
    .orElse(super.receiveRemoteEvent)

  def banAction(banningActor: ActorRef, bannedActor: ActorRef, banned: String, message: String) = {
    println(s"RemoteBannable banAction $banned")
    remoteUsers ! BanCommand(banned, message)
  }

  def unbanAction(unbanningActor: ActorRef, unbanned: String) = {
    remoteUsers ! UnbanCommand(unbanned)
  }
}
