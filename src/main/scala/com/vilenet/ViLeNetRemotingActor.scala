package com.vilenet

import java.util.concurrent.TimeUnit

import akka.actor.{ActorSelection, Address}
import akka.pattern.ask
import akka.util.Timeout
import com.vilenet.servers._

import scala.collection.mutable
import scala.concurrent.Await

/**
  * Created by filip on 1/3/17.
  */
private[vilenet] trait ViLeNetRemotingActor extends ViLeNetActor {

  val actorPath: String
  val remoteActors = new mutable.HashSet[ActorSelection] {
    def !(message: Any) = foreach(_ ! message)
    def +=(address: Address): this.type = +=(system.actorSelection(s"akka://${address.hostPort}/user/$actorPath"))
    def ++=(addresses: TraversableOnce[Address]) = addresses.foreach(+=)
    def -=(address: Address): this.type = -=(system.actorSelection(s"akka://${address.hostPort}/user/$actorPath"))
  }

  override def preStart() = {
    super.preStart()

    implicit val timeout = Timeout(5, TimeUnit.SECONDS)
    Await.result(serverRegistry ? Subscribe(self), timeout.duration) match {
      case SubscribeAck(addresses) =>
        println("Addresses for " + getClass + " - " + addresses)
        remoteActors ++= addresses
        addresses.foreach(onServerAlive)

      case reply =>
        log.error("Failed to subscribe to registry. This actor is FUCKED: {}", reply)
    }
  }

  override def aroundReceive(receive: Receive, msg: Any) = {
    super.aroundReceive(receive, msg)

    if (isLocal()) {
      msg match {
        case _: Remotable =>
          remoteActors ! msg

        case ServerAlive(address) =>
          remoteActors += address
          onServerAlive(address)

        case ServerDead(address) =>
          remoteActors -= address
          onServerDead(address)

        case _ =>
      }
    }
  }

  // Overridable for remoting actors that care
  protected def onServerAlive(address: Address) = {}
  protected def onServerDead(address: Address) = {}
}
