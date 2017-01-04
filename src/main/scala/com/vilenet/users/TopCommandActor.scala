package com.vilenet.users

import akka.actor.Props
import com.vilenet.Constants._
import com.vilenet.{ViLeNetActor, ViLeNetComponent}
import com.vilenet.channels.{User, UserInfo}
import com.vilenet.coders.commands.TopCommand
import com.vilenet.servers.RemoteEvent
import com.vilenet.utils.FiniteArrayBuffer

/**
  * Created by filip on 2/8/16.
  */
object TopCommandActor extends ViLeNetComponent {
  def apply() = system.actorOf(Props[TopCommandActor], VILE_NET_TOP_COMMAND_ACTOR)
}

class TopCommandActor extends ViLeNetActor {

  val topMap = Map(
    "binary" -> FiniteArrayBuffer[User](),
    "chat" -> FiniteArrayBuffer[User](),
    "all" -> FiniteArrayBuffer[User]()
  )

  //subscribe(TOPIC_USERS)

  override def receive: Receive = {
    case RemoteEvent(Add(actor, user, protocol)) =>
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
