package com.init6.users

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

class TopCommandActor extends Init6Actor {

  val topMap = Map(
    "binary" -> FiniteArrayBuffer[User](),
    "chat" -> FiniteArrayBuffer[User](),
    "all" -> FiniteArrayBuffer[User]()
  )

  override def receive: Receive = {
    case Add(_, user, protocol) =>
      topMap(
        protocol match {
          case Chat1Protocol | TelnetProtocol => "chat"
          case _ => "binary"
        }
      ) += user
      topMap("all") += user

    case TopCommand(which) =>
      val topList = topMap(which)
      sender() ! UserInfo(TOP_INFO(topList.getInitialSize, which))
      topList
        .zipWithIndex
        .foreach {
          case (user, i) =>
            sender() ! UserInfo(TOP_LIST(i + 1, user.name, encodeClient(user.client)))
        }
  }
}
