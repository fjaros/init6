package com.vilenet

import akka.cluster.ClusterEvent.{UnreachableMember, MemberEvent, InitialStateAsEvents}
import akka.cluster.pubsub.DistributedPubSubMediator.{Publish, Subscribe, Unsubscribe}

/**
  * Created by filip on 12/28/15.
  */
private[vilenet] trait ViLeNetClusterActor extends ViLeNetActor {

  val cluster = SystemContext.cluster
  val mediator = SystemContext.mediator

  override def preStart(): Unit = {
    super.preStart()

    cluster.subscribe(self, initialStateMode = InitialStateAsEvents,
      classOf[MemberEvent], classOf[UnreachableMember])
  }

  def subscribe(topic: String) = mediator ! Subscribe(topic, self)

  def unsubscribe(topic: String) = mediator ! Unsubscribe(topic, self)

  def publish(topic: String, msg: Any) = mediator ! Publish(topic, msg)
}
