package com.vilenet

import akka.actor.Address
import akka.cluster.ClusterEvent.{UnreachableMember, MemberEvent, InitialStateAsEvents}
import akka.cluster.pubsub.DistributedPubSubMediator.{Send, Publish, Subscribe}

/**
  * Created by filip on 12/28/15.
  */
private[vilenet] trait ViLeNetClusterActor extends ViLeNetActor {

  lazy val cluster = SystemContext.cluster
  lazy val mediator = SystemContext.mediator

  override def preStart(): Unit = {
    super.preStart()

    cluster.subscribe(self, initialStateMode = InitialStateAsEvents,
      classOf[MemberEvent], classOf[UnreachableMember])
  }

  def isLocal(address: Address): Boolean = cluster.selfAddress == address

  def subscribe(topic: String) = mediator ! Subscribe(topic, self)

  def publish(topic: String, msg: Any) = mediator ! Publish(topic, msg)

  def send(path: String, msg: Any) = mediator ! Send(path, msg, localAffinity = false)
}
