package com.init6.servers

import java.util.concurrent.TimeUnit

import akka.actor.{ActorRef, Address, Props, Terminated}
import com.init6.Constants._
import com.init6.coders.commands.Command
import com.init6.{Config, Init6Actor, Init6Component}

import scala.collection.mutable
import scala.concurrent.duration.Duration

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
object ServerRegistry extends Init6Component {
  def apply() = system.actorOf(Props[ServerRegistry].withDispatcher(SERVER_REGISTRY_DISPATCHER), INIT6_SERVER_REGISTRY_PATH)
}

// These are local only
case class Subscribe(actor: ActorRef)
case class SubscribeAck(address: Set[Address])
case class Unsubscribe(actor: ActorRef)
case object UnsubscribeAck
case class ServerAlive(address: Address)
case class ServerDead(address: Address)

// Internal
case object AreYouThere extends Command
case object IAmHere extends Command
case object CheckKeepAlives
case class ServerPinged(actor: ActorRef)

class ServerRegistry extends Init6Actor {

  val remoteServerPaths = Config().Server.remoteNodes
    .map(node => system.actorSelection(remoteAddress(node, INIT6_SERVER_REGISTRY_PATH)))

  val subscribers = mutable.HashSet[ActorRef]()
  val keepAlives = mutable.HashMap[ActorRef, Long]()

  var isSplit = false

  override def preStart() = {
    super.preStart()

    import system.dispatcher
    val initialDelay = Duration(Config().Server.Registry.initialDelay, TimeUnit.MILLISECONDS)
    val pingDelay = Duration(Config().Server.Registry.pingDelay, TimeUnit.MILLISECONDS)
    system.scheduler.schedule(
      initialDelay,
      pingDelay
    )({
      self ! CheckKeepAlives

      // TIMING OUT?
      // Weird handling in akka of .resolveOne, use custom messages here.
      log.debug("#SR Scheduler " + remoteServerPaths)
      remoteServerPaths.foreach(_.tell(AreYouThere, self))
    })
  }

  override def receive = {
    case AreYouThere =>
      log.debug("#SR AreYouThere {}", sender())
      sender() ! IAmHere

    // Internal
    case c @ IAmHere =>
      val actor = sender()
      log.debug("#SR {}", c)
      if (!keepAlives.contains(actor)) {
        val serverAlive = ServerAlive(actor.path.address)
        subscribers.foreach(_ ! serverAlive)
      }
      keepAlives += actor -> System.currentTimeMillis()

    case c @ CheckKeepAlives =>
      log.debug("#SR {}", c)
      // Prune keepAlives. Anything >= dropAfter milliseconds shall be deemed dead
      val now = System.currentTimeMillis()
      keepAlives.toSeq.foreach {
        case (actor, time) =>
          log.debug("#SR Checking " + actor + " - " + (now - time))
          if (now - time >= Config().Server.Registry.dropAfter) {
            // this actor is dead
            log.debug("#SR Removing actor " + actor)
            keepAlives -= actor
            val serverDead = ServerDead(actor.path.address)
            subscribers.foreach(_ ! serverDead)
          }
      }

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
