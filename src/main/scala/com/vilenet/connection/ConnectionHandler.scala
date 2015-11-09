package com.vilenet.connection

import java.net.InetSocketAddress

import akka.actor.{ActorRef, Props}
import akka.io.Tcp.{ResumeAccepting, Bound, Bind, Connected, Register}
import akka.io.{IO, Tcp}
import com.vilenet.ViLeNetActor


/**
 * Created by filip on 9/19/15.
 */
object ConnectionHandler {
  def apply(bindAddress: InetSocketAddress, remoteAddresses: Array[String]) = Props(new ConnectionHandler(bindAddress, remoteAddresses))
}

class ConnectionHandler(bindAddress: InetSocketAddress, remoteAddresses: Array[String]) extends ViLeNetActor {

  IO(Tcp) ! Bind(self, bindAddress, pullMode = true)

  override def receive: Receive = {
    case Bound(local) =>
      log.error("Local address {} bound", local)
      sender ! ResumeAccepting(1)
      context.become(accept(sender()))
  }

  def accept(listener: ActorRef): Receive = {
    case Connected(remote, _) =>
      log.error("Remote address {} connected", remote)
      sender ! Register(context.actorOf(ProtocolHandler(remote, sender())), keepOpenOnPeerClosed = true)
      listener ! ResumeAccepting(1)
  }
}
