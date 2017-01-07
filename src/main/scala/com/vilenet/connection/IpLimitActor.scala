package com.vilenet.connection

import akka.actor.Props
import com.vilenet.Constants._
import com.vilenet.{ViLeNetActor, ViLeNetComponent}

import scala.collection.mutable

/**
  * Created by filip on 1/9/16.
  */
object IpLimitActor extends ViLeNetComponent {
  def apply(limit: Int) = system.actorOf(Props(classOf[IpLimitActor], limit), VILE_NET_IP_LIMITER_PATH)
}

case class Connected(address: Array[Byte])
case class Disconnected(address: Array[Byte])
case object Allowed
case object NotAllowed

class IpLimitActor(limit: Int) extends ViLeNetActor {

  val ips = mutable.HashMap[Int, Int]()

  private def toDword(ip: Array[Byte]) = ip(3) << 24 | ip(2) << 16 | ip(1) << 8 | ip.head

  override def receive: Receive = {
    case Connected(address) =>
      val addressInt = toDword(address)
      val current = ips.getOrElse(addressInt, 0)

      if (limit > current) {
        sender() ! Allowed
        ips += addressInt -> (current + 1)
      } else {
        sender() ! NotAllowed
      }

    case Disconnected(address) =>
      val addressInt = toDword(address)
      val current = ips.getOrElse(addressInt, 0)
      if (current > 0) { // Should always get through the if though...
        ips += addressInt -> (current - 1)
      }
  }
}
