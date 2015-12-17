package com.vilenet.channels

import akka.actor.ActorRef
import com.vilenet.coders.commands.Command

import scala.collection.mutable

/**
  * Created by filip on 11/26/15.
  */
case class DesignateAction(actor: ActorRef, designatee: ActorRef) extends Command

trait RemoteOperableChannelActor extends RemoteChannelActor {

  // Designate Users actor -> actor
  var designatedActors = mutable.HashMap[ActorRef, ActorRef]()

  override def receiveRemoteEvent = ({
    case DesignateAction(actor, designatee) => designatedActors += actor -> designatee
  }: Receive)
    .orElse(super.receiveRemoteEvent)

  def designate(actor: ActorRef, designatee: ActorRef) = {
    remoteUsers ! DesignateAction(actor, designatee)
  }
}
