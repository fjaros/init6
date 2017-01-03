package com.vilenet

import java.util.concurrent.TimeUnit

import akka.actor.ActorRef
import akka.cluster.ClusterEvent.{InitialStateAsEvents, MemberEvent, UnreachableMember}
import akka.cluster.pubsub.DistributedPubSubMediator._
import akka.pattern.ask
import akka.util.Timeout

import scala.concurrent.Await

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

  // Make this a blocking call (don't advance subscribing actor until subscribe is acked)
  def subscribe(topic: String, actor: ActorRef): Boolean = {
    mediator ! Subscribe(topic, actor)
    true
//    implicit val timeout = Timeout(1, TimeUnit.SECONDS)
//    Await.result(mediator ? Subscribe(topic, actor), timeout.duration) match {
//      case SubscribeAck(Subscribe(_topic, _, _)) => _topic == topic
//      case _ => false
//    }
  }
  def subscribe(topic: String): Boolean = subscribe(topic, self)

  def unsubscribe(topic: String, actor: ActorRef): Boolean = {
    implicit val timeout = Timeout(1, TimeUnit.SECONDS)
    Await.result(mediator ? Unsubscribe(topic, actor), timeout.duration) match {
      case UnsubscribeAck(Unsubscribe(_topic, _, _)) => _topic == topic
      case _ => false
    }
  }
  def unsubscribe(topic: String): Boolean = unsubscribe(topic, self)

  def publish(topic: String, message: Any): Unit = mediator ! Publish(topic, message)
  def publish(message: Any)(topic: String): Unit = publish(topic, message)
}
