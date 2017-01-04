package com.vilenet

import java.util.concurrent.TimeUnit

import akka.actor.{Actor, ActorLogging, ActorRef, Address}
import akka.pattern.ask
import akka.util.Timeout
import com.vilenet.servers.{Subscribe, SubscribeAck}

import scala.collection.mutable
import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.util.{Failure, Success}

/**
 * Created by filip on 9/19/15.
 */
private[vilenet] trait ViLeNetActor extends Actor with ActorLogging with ViLeNetComponent {

  def isLocal(address: Address): Boolean = self.path.address == address
  def isLocal(actor: ActorRef): Boolean = isLocal(actor.path.address)
  def isLocal(): Boolean = isLocal(sender())

  def isRemote(address: Address): Boolean = !isLocal(address)
  def isRemote(actor: ActorRef): Boolean = isRemote(actor.path.address)
  def isRemote(): Boolean = isRemote(sender())

//  def resolveRemote[A, B](address: Address, actorPath: String, onSuccess: A => B): Unit = {
//    val resolvedPath = s"akka://${address.hostPort}/$actorPath"
//
//    val failedRunnable = new Runnable {
//      override def run() = resolveRemote(address, actorPath, onSuccess)
//    }
//
//    resolveRemote(resolvedPath, onSuccess, failedRunnable)
//  }
//
//  // Internal recursive for resolving in case of failure
//  private def resolveRemote[A, B](resolvedPath: String, successFunc: A => B, failedRunnable: Runnable) = {
//    system.actorSelection(resolvedPath)
//      .resolveOne(Timeout(5, TimeUnit.SECONDS).duration).onComplete {
//        case Success(message) =>
//          successFunc(message)
//
//        case Failure(ex) =>
//          system.scheduler.scheduleOnce(Timeout(500, TimeUnit.MILLISECONDS).duration, failedRunnable)
//    }
//  }
}
