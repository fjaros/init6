package com.init6.connection

import java.net.{InetAddress, InetSocketAddress}

import akka.actor.{ActorRef, Props}
import com.init6.Constants._
import com.init6.channels.UserInfoArray
import com.init6.coders.IPUtils
import com.init6.coders.commands.PrintConnectionLimit
import com.init6.{Config, Init6Actor, Init6Component}

import scala.collection.mutable

/**
  * Created by filip on 1/9/16.
  */
object IpLimitActor extends Init6Component {
  def apply(limit: Int) = system.actorOf(Props(classOf[IpLimitActor], limit), INIT6_IP_LIMITER_PATH)
}

case class Connected(connectingActor: ActorRef, address: InetSocketAddress)
case class Disconnected(connectingActor: ActorRef)
case class Allowed(connectingActor: ActorRef, address: InetSocketAddress)
case class NotAllowed(connectingActor: ActorRef, address: InetSocketAddress)
case class IpBan(address: InetSocketAddress, until: Long)

class IpLimitActor(limit: Int) extends Init6Actor {

  val actorToIp = mutable.HashMap.empty[ActorRef, Int]
  val ipCount = mutable.HashMap.empty[Int, Int]
  val ipBanned = mutable.HashMap.empty[Int, Long]

  override def receive: Receive = {
    case Connected(connectingActor, address) =>
      if (
        Config().Accounts.enableIpWhitelist &&
        !Config().Accounts.ipWhitelist.contains(address.getAddress.getHostAddress)
      ) {
        sender() ! NotAllowed(connectingActor, address)
        return receive
      }

      val addressInt = IPUtils.toDword(address.getAddress.getAddress)
      val current = ipCount.getOrElse(addressInt, 0)
      val isIpBanned = ipBanned.get(addressInt).exists(until => {
        if (System.currentTimeMillis >= until) {
          ipBanned -= addressInt
          false
        } else {
          true
        }
      })

      if (limit > current && !isIpBanned) {
        actorToIp += connectingActor -> addressInt
        ipCount += addressInt -> (current + 1)
        sender() ! Allowed(connectingActor, address)
      } else {
        sender() ! NotAllowed(connectingActor, address)
      }

    case Disconnected(connectingActor) =>
      actorToIp
        .get(connectingActor)
        .foreach(addressInt => {
          val current = ipCount.getOrElse(addressInt, 0)
          if (current > 0) {
            // Should always get through the if though...
            ipCount += addressInt -> (current - 1)
          }
          actorToIp -= connectingActor
        })

    case IpBan(address, until) =>
      val addressInt = IPUtils.toDword(address.getAddress.getAddress)
      ipBanned += addressInt -> until

    case PrintConnectionLimit =>
      sender() ! UserInfoArray(
        ipCount.map {
          case (ipDword, count) =>
            s"${IPUtils.toString(ipDword)} - $count"
        }.toArray
      )
  }
}
