package com.vilenet

import akka.actor.{ActorLogging, Actor}

/**
 * Created by filip on 9/19/15.
 */
private[vilenet] trait ViLeNetActor extends Actor with ActorLogging with ViLeNetComponent {

  def isLocal(): Boolean = self.path.address == sender().path.address
  def isRemote(): Boolean = !isLocal()
}
