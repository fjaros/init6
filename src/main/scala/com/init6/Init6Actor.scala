package com.init6

import akka.actor.{Actor, ActorLogging, ActorRef, Address}

/**
 * Created by filip on 9/19/15.
 */
private[init6] trait Init6Actor extends Actor with ActorLogging with Init6Component {

  def isLocal(address: Address): Boolean = self.path.address == address
  def isLocal(actor: ActorRef): Boolean = isLocal(actor.path.address)
  def isLocal(): Boolean = isLocal(sender())

  def isRemote(address: Address): Boolean = !isLocal(address)
  def isRemote(actor: ActorRef): Boolean = isRemote(actor.path.address)
  def isRemote(): Boolean = isRemote(sender())

}
