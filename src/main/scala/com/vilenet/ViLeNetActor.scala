package com.vilenet

import akka.actor.{Actor, ActorLogging, ActorRef, Address}

/**
 * Created by filip on 9/19/15.
 */
private[vilenet] trait ViLeNetActor extends Actor with ActorLogging with ViLeNetComponent {

  def isLocal(address: Address): Boolean = self.path.address == address
  def isLocal(actor: ActorRef): Boolean = isLocal(actor.path.address)
  def isLocal(): Boolean = isLocal(sender())

  def isRemote(address: Address): Boolean = !isLocal(address)
  def isRemote(actor: ActorRef): Boolean = isRemote(actor.path.address)
  def isRemote(): Boolean = isRemote(sender())
}
