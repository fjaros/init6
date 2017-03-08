package com.init6.connection

import java.net.InetSocketAddress

import akka.actor.{ActorRef, FSM, Props}
import akka.io.Tcp.{Abort, Bind, ConnectionClosed, Register, ResumeAccepting}
import akka.io.{IO, Tcp}
import com.init6.users.ConnectedActor
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
      val rawConnectionInfo = ConnectionInfo(remote, sender(), getAcceptingUptime.toNanos)
      val connectedTime = getAcceptingUptime.toNanos
      log.debug("Address {} connected", remote.getAddress.getHostAddress)
      ipLimiterActor ! Connected(rawConnectionInfo)
      stay()
    case Event(Allowed(rawConnectionInfo), listener: ActorRef) =>
      allowed(rawConnectionInfo)
      listener ! ResumeAccepting(1)
      stay()
    case Event(NotAllowed(rawConnectionInfo), listener: ActorRef) =>
      notAllowed(rawConnectionInfo)
      listener ! ResumeAccepting(1)
      stay()
    case Event(_: ConnectionClosed, _) =>
      ipLimiterActor ! Disconnected(sender())
      stay()
  }

  def allowed(rawConnectionInfo: ConnectionInfo) = {
    log.debug("Address {} allowed", rawConnectionInfo.ipAddress.getAddress)
    rawConnectionInfo.actor ! Register(context.actorOf(ProtocolHandler(rawConnectionInfo)), keepOpenOnPeerClosed = true)
    rawConnectionInfo.actor ! Tcp.SO.KeepAlive(on = true)
  }

  def notAllowed(rawConnectionInfo: ConnectionInfo) = {
    log.debug("Address {} disallowed", rawConnectionInfo.ipAddress.getAddress)
    rawConnectionInfo.actor ! Abort
  }
}
