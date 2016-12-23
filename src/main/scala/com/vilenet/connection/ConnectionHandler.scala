package com.vilenet.connection

import java.net.InetSocketAddress
import java.util.concurrent.TimeUnit

import akka.actor.{ActorRef, Props}
import akka.pattern.ask
import akka.io.Tcp.{Abort, Bind, Bound, Register, ResumeAccepting}
import akka.io.{IO, Tcp}
import akka.util.Timeout
import com.vilenet.Constants._
import com.vilenet.{ViLeNetClusterActor, ViLeNetComponent}

import scala.concurrent.Await
import scala.util.Try


/**
 * Created by filip on 9/19/15.
 */
object ConnectionHandler extends ViLeNetComponent {
  def apply(bindAddress: InetSocketAddress) = system.actorOf(Props(classOf[ConnectionHandler], bindAddress), VILE_NET)
}

class ConnectionHandler(bindAddress: InetSocketAddress) extends ViLeNetClusterActor {

  implicit val timeout = Timeout(200, TimeUnit.MILLISECONDS)

  IO(Tcp) ! Bind(self, bindAddress, pullMode = true)

  override def receive: Receive = {
    case Bound(local) =>
      log.error("Local address {} bound", local)
      sender ! ResumeAccepting(1)
      context.become(accept(sender()))
  }

  def accept(listener: ActorRef): Receive = {
    case Tcp.Connected(remote, _) =>
      val remoteInetAddress = remote.getAddress
      log.debug("Address {} connected", remoteInetAddress)
      Try {
        Await.result(ipLimiterActor ? Connected(remoteInetAddress.getAddress), timeout.duration) match {
          case Allowed => allowed(remote)
          case _ => disallowed(remote)
        }
      }.getOrElse(disallowed(remote))
      listener ! ResumeAccepting(1)
  }

  def allowed(remote: InetSocketAddress) = {
    log.debug("Address {} allowed", remote.getAddress)
    sender() ! Register(context.actorOf(ProtocolHandler(remote, sender())), keepOpenOnPeerClosed = true)
    sender() ! Tcp.SO.KeepAlive(on = true)
  }

  def disallowed(remote: InetSocketAddress) = {
    log.debug("Address {} disallowed", remote.getAddress)
    sender() ! Abort
  }
}
