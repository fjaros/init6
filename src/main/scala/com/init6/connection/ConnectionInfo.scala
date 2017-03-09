package com.init6.connection

import java.net.InetSocketAddress

import akka.actor.ActorRef
import com.init6.users.{NotYetKnownProtocol, Protocol}

/**
  * Created by filip on 3/7/17.
  */
case class ConnectionInfo(
  ipAddress: InetSocketAddress,
  actor: ActorRef,
  connectedTime: Long,
  place: Int = -1,
  protocol: Protocol = NotYetKnownProtocol
)
