package com.init6.connection

import akka.actor.{ActorRef, Props}
import com.init6.Constants._
import com.init6.channels.{UserInfo, UserInfoArray}
import com.init6.coders.IPUtils
import com.init6.coders.commands.{PrintConnectionLimit, UnIpBanCommand}
import com.init6.{Config, Init6Component, Init6RemotingActor}

import scala.collection.mutable

/**
  * Created by filip on 1/9/16.
  */
object IpLimitActor extends Init6Component {
  def apply(limit: Int) = system.actorOf(Props(classOf[IpLimitActor], limit), INIT6_IP_LIMITER_PATH)
}

case class Connected(connectionInfo: ConnectionInfo)
case class Disconnected(connectingActor: ActorRef)
case class Allowed(connectionInfo: ConnectionInfo)
case class NotAllowed(connectionInfo: ConnectionInfo)
case class IpBan(address: Array[Byte], until: Long)

class IpLimitActor(limit: Int) extends Init6RemotingActor {

  override val actorPath = INIT6_IP_LIMITER_PATH

  val actorToIp = mutable.HashMap.empty[ActorRef, Int]
  val ipCount = mutable.HashMap.empty[Int, Int]
  val ipConnectionTimes = mutable.HashMap.empty[Int, mutable.PriorityQueue[Long]]
  val ipBanned = mutable.HashMap.empty[Int, Long]

  def addIpConnection(addressInt: Int) = {
    val t = System.currentTimeMillis

    if (Config().AntiFlood.ReconnectLimit.enabled &&
      getAcceptingUptime.toSeconds >= Config().AntiFlood.ReconnectLimit.ignoreAtStartFor) {

      ipConnectionTimes
        .get(addressInt)
        .fold({
          ipConnectionTimes += addressInt -> mutable.PriorityQueue(t)(Ordering[Long].reverse)
          true
        })(queue => {
          // more than 20 times within 1 min
          val allowed = Config().AntiFlood.ReconnectLimit.inPeriod
          while (queue.nonEmpty && t - queue.head >= allowed) {
            queue.dequeue()
          }
          queue += t
          queue.length < Config().AntiFlood.ReconnectLimit.times
        })
    } else {
      true
    }
  }

  override def receive: Receive = {
    case Connected(connectionInfo) =>
      if (
        Config().Accounts.enableIpWhitelist &&
        !Config().Accounts.ipWhitelist.contains(connectionInfo.ipAddress.getAddress.getHostAddress)
      ) {
        sender() ! NotAllowed(connectionInfo)
        return receive
      }

      val addressInt = IPUtils.bytesToDword(connectionInfo.ipAddress.getAddress.getAddress)
      val current = ipCount.getOrElse(addressInt, 0)
      if (!addIpConnection(addressInt)) {
        ipBanned += addressInt -> (System.currentTimeMillis() + (Config().AntiFlood.ReconnectLimit.ipBanTime * 1000))
      }
      val isIpBanned = ipBanned.get(addressInt).exists(until => {
        if (System.currentTimeMillis >= until) {
          ipBanned -= addressInt
          false
        } else {
          true
        }
      })

      if (limit > current && !isIpBanned) {
        actorToIp += connectionInfo.actor -> addressInt
        ipCount += addressInt -> (current + 1)
        sender() ! Allowed(connectionInfo)
      } else {
        sender() ! NotAllowed(connectionInfo)
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
      val addressInt = IPUtils.bytesToDword(address)
      ipBanned += addressInt -> until
      sender() ! UserInfo(IPBANNED(IPUtils.dwordToString(addressInt)))

    case UnIpBanCommand(address) =>
      val addressInt = IPUtils.bytesToDword(address)
      ipBanned -= addressInt
      sender() ! UserInfo(UNIPBANNED(IPUtils.dwordToString(addressInt)))

    case PrintConnectionLimit =>
      sender() ! UserInfoArray(
        ipCount.map {
          case (ipDword, count) =>
            s"${IPUtils.dwordToString(ipDword)} - $count"
        }.toArray
      )
  }
}
