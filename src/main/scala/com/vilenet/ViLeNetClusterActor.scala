package com.vilenet

import akka.actor.Address
import akka.cluster.ClusterEvent.{UnreachableMember, MemberEvent, InitialStateAsEvents}

/**
  * Created by filip on 12/28/15.
  */
private[vilenet] trait ViLeNetClusterActor extends ViLeNetActor {

  def isLocal(address: Address): Boolean = cluster.selfAddress == address

  override def preStart(): Unit = {
    super.preStart()

    cluster.subscribe(self, initialStateMode = InitialStateAsEvents,
      classOf[MemberEvent], classOf[UnreachableMember])
  }
}
