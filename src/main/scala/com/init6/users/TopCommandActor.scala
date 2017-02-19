package com.init6.users

import akka.actor.{ActorRef, Props}
import com.init6.Constants._
import com.init6.{Config, Init6Actor, Init6Component, SystemContext}
import com.init6.channels.{User, UserInfo}
import com.init6.coders.commands.TopCommand
import com.init6.utils.FiniteLinkedHashMap

/**
  * Created by filip on 2/8/16.
  */
object TopCommandActor extends Init6Component {
  def apply() = system.actorOf(Props[TopCommandActor], INIT6_TOP_COMMAND_ACTOR)
}

case class TopInfo(user: User, loggedInTime: Long)
case class UserChannelChanged(actor: ActorRef, user: User)

class TopCommandActor extends Init6Actor {

  val topMap = Map(
    "binary" -> FiniteLinkedHashMap[ActorRef, TopInfo](),
    "chat" -> FiniteLinkedHashMap[ActorRef, TopInfo](),
    "all" -> FiniteLinkedHashMap[ActorRef, TopInfo]()
  )

  override def receive: Receive = {
    case Add(ipAddress, userActor, user, protocol) =>
      val topInfo = TopInfo(user, getAcceptingUptime.toNanos)
      topMap(
        protocol match {
          case Chat1Protocol | TelnetProtocol => "chat"
          case _ => "binary"
        }
      ) += userActor -> topInfo
      topMap("all") += userActor -> topInfo

    case UserChannelChanged(actor, user) =>
      val topMapId =
        if (isChatProtocol(user.client)) {
          "chat"
        } else {
          "binary"
        }

      topMap(topMapId)
        .get(actor)
        .filter(_.user.inChannel.isEmpty)
        .foreach(topInfo => {
          val newEntry = actor -> topInfo.copy(user = user)

          if (isChatProtocol(user.client)) {
            if (!user.inChannel.equalsIgnoreCase("chat")) {
              topMap("chat") += newEntry
              topMap("all") += newEntry
            }
          } else {
            topMap("binary") += newEntry
            topMap("all") += newEntry
          }
        })

    case TopCommand(_, which) =>
      val topList = topMap(which)
      sender() ! UserInfo(TOP_INFO(topList.getInitialSize, which, Config().Server.host))
      topList
        .values
        .zipWithIndex
        .foreach {
          case (TopInfo(user, loggedInTime), i) =>
            sender() ! UserInfo(TOP_LIST(i + 1, user.name, user.client, user.inChannel, loggedInTime))
        }
  }
}
