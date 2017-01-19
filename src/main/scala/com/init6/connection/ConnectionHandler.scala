package com.init6.connection

import java.net.InetSocketAddress
import java.util.concurrent.TimeUnit

import akka.actor.{ActorRef, Props}
import akka.pattern.ask
import akka.io.Tcp.{Abort, Bind, Bound, Register, ResumeAccepting}
import akka.io.{IO, Tcp}
import akka.util.Timeout
import com.init6.{Init6Actor, Init6Component}

import scala.concurrent.Await
import scala.util.Try

/**
 * Created by filip on 9/19/15.
 */
object ConnectionHandler extends Init6Component {
  def apply(host: String, port: Int) = system.actorOf(Props(classOf[ConnectionHandler], host, port))
}

class ConnectionHandler(host: String, port: Int) extends Init6Actor {

  implicit val timeout = Timeout(200, TimeUnit.MILLISECONDS)

  override def preStart() = {
    super.preStart()

    val bindAddress = new InetSocketAddress(host, port)
    IO(Tcp) ! Bind(self, bindAddress, pullMode = true)
  }

  override def receive: Receive = {
    case Bound(local) =>
      log.error("Local address {} bound", local)
      sender ! ResumeAccepting(1)
      context.become(accept(sender()))
  }

  def accept(listener: ActorRef): Receive = {
    case Tcp.Connected(remote, _) =>
      val remoteInetAddress = remote.getAddress
      log.debug("Address {} connected", remoteInetAddress.getHostAddress)
      Try {
        Await.result(ipLimiterActor ? Connected(remoteInetAddress), timeout.duration) match {
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
