package com.init6.users

import java.text.DecimalFormat

import akka.actor.Props
import com.init6.Constants._
import com.init6.{Init6Actor, Init6Component}
import com.init6.channels.{User, UserInfo}
import com.init6.coders.commands.TopCommand
import com.init6.utils.FiniteArrayBuffer

/**
  * Created by filip on 2/8/16.
  */
object TopCommandActor extends Init6Component {
  def apply() = system.actorOf(Props[TopCommandActor], INIT6_TOP_COMMAND_ACTOR)
}

case class TopInfo(user: User, loggedInTime: String)

class TopCommandActor extends Init6Actor {

  val decimalFormat = new DecimalFormat("#.000")

  val topMap = Map(
    "binary" -> FiniteArrayBuffer[TopInfo](),
    "chat" -> FiniteArrayBuffer[TopInfo](),
    "all" -> FiniteArrayBuffer[TopInfo]()
  )

  override def receive: Receive = {
    case Add(_, user, protocol) =>
      val topInfo = TopInfo(user, decimalFormat.format(math.round((System.nanoTime() - getBindTime).toDouble / 1000).toDouble / 1000))
      topMap(
        protocol match {
          case Chat1Protocol | TelnetProtocol => "chat"
          case _ => "binary"
        }
      ) += topInfo
      topMap("all") += topInfo

    case TopCommand(which) =>
      val topList = topMap(which)
      sender() ! UserInfo(TOP_INFO(topList.getInitialSize, which))
      topList
        .zipWithIndex
        .foreach {
          case (topInfo, i) =>
            sender() ! UserInfo(TOP_LIST(i + 1, topInfo.user.name, encodeClient(topInfo.user.client), topInfo.loggedInTime))
        }
  }
}
