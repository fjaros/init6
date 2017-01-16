package com.init6.connection

import java.net.InetAddress

import akka.actor.Props
import com.init6.Constants._
import com.init6.{Config, Init6Actor, Init6Component}

import scala.collection.mutable

/**
  * Created by filip on 1/9/16.
  */
object IpLimitActor extends Init6Component {
  def apply(limit: Int) = system.actorOf(Props(classOf[IpLimitActor], limit), INIT6_IP_LIMITER_PATH)
}

case class Connected(address: InetAddress)
case class Disconnected(address: InetAddress)
case object Allowed
case object NotAllowed

class IpLimitActor(limit: Int) extends Init6Actor {

  val ips = mutable.HashMap[Int, Int]()

  private def toDword(ip: Array[Byte]) = ip(3) << 24 | ip(2) << 16 | ip(1) << 8 | ip.head

  override def receive: Receive = {
    case Connected(address) =>
      if (
        Config().Accounts.enableIpWhitelist &&
        !Config().Accounts.ipWhitelist.contains(address.getHostAddress)
      ) {
        sender() ! NotAllowed
        return receive
      }

      val addressInt = toDword(address.getAddress)
      val current = ips.getOrElse(addressInt, 0)

      if (limit > current) {
        sender() ! Allowed
        ips += addressInt -> (current + 1)
      } else {
        sender() ! NotAllowed
      }

    case Disconnected(address) =>
      val addressInt = toDword(address.getAddress)
      val current = ips.getOrElse(addressInt, 0)
      if (current > 0) { // Should always get through the if though...
        ips += addressInt -> (current - 1)
      }
  }
}