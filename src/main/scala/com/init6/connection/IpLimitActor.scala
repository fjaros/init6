package com.init6.connection

import java.net.InetSocketAddress

import akka.actor.{ActorRef, Props}
import com.init6.Constants._
import com.init6.{Config, Init6Actor, Init6Component}

import scala.collection.mutable

/**
  * Created by filip on 1/9/16.
  */
object IpLimitActor extends Init6Component {
  def apply(limit: Int) = system.actorOf(Props(classOf[IpLimitActor], limit), INIT6_IP_LIMITER_PATH)
}

case class Connected(connectingActor: ActorRef, address: InetSocketAddress)
case class Disconnected(address: InetSocketAddress)
case class Allowed(connectingActor: ActorRef, address: InetSocketAddress)
case class NotAllowed(connectingActor: ActorRef, address: InetSocketAddress)

class IpLimitActor(limit: Int) extends Init6Actor {

  val ips = mutable.HashMap[Int, Int]()

  private def toDword(ip: Array[Byte]) = ip(3) << 24 | ip(2) << 16 | ip(1) << 8 | ip.head

  override def receive: Receive = {
    case Connected(connectingActor, address) =>
      if (
        Config().Accounts.enableIpWhitelist &&
        !Config().Accounts.ipWhitelist.contains(address.getAddress.getHostAddress)
      ) {
        sender() ! NotAllowed(connectingActor, address)
        return receive
      }

      val addressInt = toDword(address.getAddress.getAddress)
      val current = ips.getOrElse(addressInt, 0)

      if (limit > current) {
        sender() ! Allowed(connectingActor, address)
        ips += addressInt -> (current + 1)
      } else {
        sender() ! NotAllowed(connectingActor, address)
      }

    case Disconnected(address) =>
      val addressInt = toDword(address.getAddress.getAddress)
      val current = ips.getOrElse(addressInt, 0)
      if (current > 0) { // Should always get through the if though...
        ips += addressInt -> (current - 1)
      }
  }
}
