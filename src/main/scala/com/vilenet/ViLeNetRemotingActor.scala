package com.vilenet

import java.util.concurrent.TimeUnit

import akka.actor.{ActorSelection, Address}
import akka.pattern.ask
import akka.util.Timeout
import com.vilenet.servers._

import scala.collection.mutable
import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success}

/**
  * Created by filip on 1/3/17.
  */
private[vilenet] trait ViLeNetRemotingActor extends ViLeNetActor {

  val actorPath: String
  val remoteActorSelection = (address: Address) => system.actorSelection(s"akka://${address.hostPort}/user/$actorPath")
  val remoteActors = mutable.HashSet.empty[ActorSelection]

  override def preStart() = {
    super.preStart()

    implicit val timeout = Timeout(5, TimeUnit.SECONDS)
    Await.result(serverRegistry ? Subscribe(self), timeout.duration) match {
      case SubscribeAck(addresses) =>
        //println("Addresses for " + getClass + " - " + addresses)
        remoteActors ++= addresses.map(remoteActorSelection)
        addresses.foreach(onServerAlive)

      case reply =>
        log.error("Failed to subscribe to registry. This actor is FUCKED: {}", reply)
    }
  }

  override def aroundReceive(receive: Receive, msg: Any) = {
    super.aroundReceive(receive, msg)

    if (isLocal()) {
      msg match {
        case _: NonRemotable =>
          // no-op

        case _: Remotable =>
          //println("#SEND " + remoteActors + " - " + originalSender + " - " + msg)
          remoteActors.foreach(_.forward(msg))

        case ServerAlive(address) =>
          val aliveSelection = remoteActorSelection(address)
          if (!remoteActors.contains(aliveSelection)) {
            remoteActors += aliveSelection
            onServerAlive(address)
          }

        case ServerDead(address) =>
          remoteActors -= remoteActorSelection(address)
          onServerDead(address)

        case _ =>
      }
    }
  }

  protected def resolveRemote(address: Address, actorPath: String, onSuccess: => Unit): Unit = {
    val resolvedPath = s"akka://${address.hostPort}/user/$actorPath"

    val failedRunnable = new Runnable {
      override def run() = resolveRemote(address, actorPath, onSuccess)
    }

    resolveRemote(resolvedPath, onSuccess, failedRunnable)
  }

  // Internal recursive for resolving in case of failure
  private def resolveRemote(resolvedPath: String, successFunc: => Unit, failedRunnable: Runnable) = {
    system.actorSelection(resolvedPath)
      .resolveOne(Timeout(2, TimeUnit.SECONDS).duration).onComplete {
      case Success(_) =>
        successFunc

      case Failure(ex) =>
        system.scheduler.scheduleOnce(Timeout(500, TimeUnit.MILLISECONDS).duration, failedRunnable)
    }
  }

  // Overridable for remoting actors that care
  protected def onServerAlive(address: Address) = {}
  protected def onServerDead(address: Address) = {}
}
