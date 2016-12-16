package com.vilenet.connection

import java.net.InetSocketAddress
import java.util.concurrent.TimeUnit

import akka.actor.{ActorRef, Props}
import akka.pattern.ask
import akka.io.Tcp.{Bind, Bound, Close, Register, ResumeAccepting}
import akka.io.{IO, Tcp}
import akka.util.Timeout
import com.vilenet.Constants._
import com.vilenet.{ViLeNetComponent, ViLeNetClusterActor}

import scala.concurrent.Await


/**
 * Created by filip on 9/19/15.
 */
object ConnectionHandler extends ViLeNetComponent {
  def apply(bindAddress: InetSocketAddress) = system.actorOf(Props(classOf[ConnectionHandler], bindAddress), VILE_NET)
}

class ConnectionHandler(bindAddress: InetSocketAddress) extends ViLeNetClusterActor {

  implicit val timeout = Timeout(100, TimeUnit.MILLISECONDS)

  IO(Tcp) ! Bind(self, bindAddress, pullMode = true)

  override def receive: Receive = {
    case Bound(local) =>
      //log.error("Local address {} bound", local)
      sender ! ResumeAccepting(1)
      context.become(accept(sender()))
  }

  def accept(listener: ActorRef): Receive = {
    case Tcp.Connected(remote, _) =>
      Await.result(ipLimiterActor ? Connected(remote.getAddress.getAddress), timeout.duration) match {
        case Allowed =>
          sender() ! Register(context.actorOf(ProtocolHandler(remote, sender())), keepOpenOnPeerClosed = true)
          sender() ! Tcp.SO.KeepAlive(on = true)
          listener ! ResumeAccepting(1)
        case _ =>
          sender() ! Close
      }

      //log.error("Remote address {} connected", remote)
  }
}
