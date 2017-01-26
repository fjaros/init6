package com.init6.connection

import java.net.InetSocketAddress

import akka.actor.{ActorRef, FSM, Props}
import akka.io.Tcp.{Abort, Bind, Register, ResumeAccepting}
import akka.io.{IO, Tcp}
import com.init6.{Init6Actor, Init6Component}

/**
 * Created by filip on 9/19/15.
 */
object ConnectionHandler extends Init6Component {
  def apply(host: String, port: Int) = system.actorOf(Props(classOf[ConnectionHandler], host, port))
}

sealed trait ConnectionHandlerState
case object Unbound extends ConnectionHandlerState
case object Bound extends ConnectionHandlerState

class ConnectionHandler(host: String, port: Int)
  extends Init6Actor with FSM[ConnectionHandlerState, ActorRef] {

  override def preStart() = {
    super.preStart()

    startWith(Unbound, ActorRef.noSender)

    val bindAddress = new InetSocketAddress(host, port)
    IO(Tcp) ! Bind(self, bindAddress, pullMode = true)
  }

  when(Unbound) {
    case Event(Tcp.Bound(localAddress), _) =>
      log.debug("Local address {} bound", localAddress)
      val listener = sender()

      listener ! ResumeAccepting(1)
      setAcceptingUptime()
      goto(Bound) using listener
  }

  when(Bound) {
    case Event(Tcp.Connected(remote, _), _) =>
      log.debug("Address {} connected", remote.getAddress.getHostAddress)
      ipLimiterActor ! Connected(sender(), remote)
      stay()
    case Event(Allowed(connectingActor, address), listener: ActorRef) =>
      allowed(connectingActor, address)
      listener ! ResumeAccepting(1)
      stay()
    case Event(NotAllowed(connectingActor, address), listener: ActorRef) =>
      notAllowed(connectingActor, address)
      listener ! ResumeAccepting(1)
      stay()
  }

  def allowed(connectingActor: ActorRef, remote: InetSocketAddress) = {
    log.debug("Address {} allowed", remote.getAddress)
    connectingActor ! Register(context.actorOf(ProtocolHandler(remote, connectingActor)), keepOpenOnPeerClosed = true)
    connectingActor ! Tcp.SO.KeepAlive(on = true)
  }

  def notAllowed(connectingActor: ActorRef, remote: InetSocketAddress) = {
    log.debug("Address {} disallowed", remote.getAddress)
    connectingActor ! Abort
  }
}
