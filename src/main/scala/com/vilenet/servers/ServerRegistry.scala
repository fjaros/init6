package com.vilenet.servers

import java.util.concurrent.TimeUnit

import akka.actor.{ActorRef, Address, Props, Terminated}
import com.vilenet.Constants._
import com.vilenet.{Config, ViLeNetActor, ViLeNetComponent}

import scala.collection.mutable
import scala.concurrent.duration.Duration
import scala.util.{Failure, Success}

/**
  * This is a registry actor, responsible for replacing the "clustering" functionality of Akka.
  *
  * The reason for not using clustering is simple - in order to do "splits" properly
  * (where one server drops from the cluster but is still online and functioning for its connected users).
  * To do splits, we'd have to remove the server from the cluster, however this means it will get quarantined,
  * and will no longer be able to join the cluster again. This is not the intended behavior.
  *
  * Also distributed pub sub mediator has an undetermined period of time during which subscriptions are distributed to the cluster
  * in which case events are missed and it hard playing catchup.
  *
  * Created by filip on 1/2/17.
  */
object ServerRegistry extends ViLeNetComponent {
  def apply() = system.actorOf(Props[ServerRegistry].withDispatcher(SERVER_REGISTRY_DISPATCHER), VILE_NET_SERVER_REGISTRY_PATH)
}

// These are local only
case class Subscribe(actor: ActorRef)
case class SubscribeAck(address: Set[Address])
case class Unsubscribe(actor: ActorRef)
case object UnsubscribeAck
case class ServerAlive(address: Address)
case class ServerDead(address: Address)


class ServerRegistry extends ViLeNetActor {

  val remoteServerPaths =
    Config.Server.nodes
      .map(node => s"akka://$VILE_NET@$node/user/$VILE_NET_SERVER_REGISTRY_PATH")

  val subscribers = mutable.HashSet[ActorRef]()
  val keepAlives = mutable.HashMap[ActorRef, Long]()

  var isSplit = false

  override def preStart() = {
    import system.dispatcher

    system.scheduler.schedule(
      Duration(500, TimeUnit.MILLISECONDS),
      Duration(10000, TimeUnit.MILLISECONDS)
    )({
      // Prune keepAlives. Anything >= 30 seconds shall be deemed dead
      val now = System.currentTimeMillis()
      keepAlives.foreach {
        case (actor, time) =>
          if (now - time >= 30000) {
            // this actor is dead
            keepAlives -= actor
            val serverDead = ServerDead(actor.path.address)
            subscribers.foreach(_ ! serverDead)
          }
      }

      // TIMING OUT?
      remoteServerPaths
        .map(system.actorSelection(_).resolveOne(Duration(10, TimeUnit.SECONDS)))
        .foreach(_.onComplete {
          case Success(actor) =>
            if (!keepAlives.contains(actor)) {
              val serverAlive = ServerAlive(actor.path.address)
              subscribers.foreach(_ ! serverAlive)
            }
            keepAlives += actor -> System.currentTimeMillis()

          case Failure(ex) =>
            log.error("Failed to contact remote server: {}", ex.getMessage)
        })
    })
  }

  override def receive = {
    // From local actors subscribing
    case Subscribe(subscriber) =>
      subscribe(subscriber)
      sender() ! SubscribeAck(keepAlives.keys.map(_.path.address).toSet)

    case Unsubscribe(subscriber) =>
      unsubscribe(subscriber)
      sender() ! UnsubscribeAck

    case Terminated(actor) =>
      unsubscribe(actor)

    // Handling of split events
    case SplitMe =>
      // split. Stop responding to events
      isSplit = true

    case SendBirth =>
      // reconnected. Start responding to events
      isSplit = false
  }

  def subscribe(subscriber: ActorRef) = {
    context.watch(subscriber)
    subscribers += subscriber
  }

  def unsubscribe(subscriber: ActorRef) = {
    context.unwatch(subscriber)
    subscribers -= subscriber
  }
}