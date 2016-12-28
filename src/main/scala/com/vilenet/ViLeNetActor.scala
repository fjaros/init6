package com.vilenet

import akka.actor.{Actor, ActorLogging, ActorRef, Address}

/**
 * Created by filip on 9/19/15.
 */
private[vilenet] trait ViLeNetActor extends Actor with ActorLogging with ViLeNetComponent {

  def isLocal(address: Address): Boolean = self.path.address == address
  def isLocal(actor: ActorRef): Boolean = isLocal(actor.path.address)
  def isLocal(): Boolean = isLocal(sender())
  def isRemote(): Boolean = !isLocal
}
