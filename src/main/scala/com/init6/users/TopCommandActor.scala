package com.init6.users

import akka.actor.{ActorRef, Props}
import com.init6.Constants._
import com.init6.channels.{User, UserInfo}
import com.init6.coders.commands.TopCommand
import com.init6.connection.ConnectionInfo
import com.init6.utils.TopInfoSeq
import com.init6.{Config, Init6Actor, Init6Component}

/**
  * Created by filip on 2/8/16.
  */
object TopCommandActor extends Init6Component {
  def apply() = system.actorOf(Props[TopCommandActor], INIT6_TOP_COMMAND_PATH)
}

case class ConnectedActor(connectionActor: ActorRef, connectionTime: Long)
case class UserChannelJoined(connectionInfo: ConnectionInfo, user: User, channelSize: Int)

case class TopInfo(user: User, connectionInfo: ConnectionInfo, joinedPlace: Int)

class TopCommandActor extends Init6Actor {

  val topMap = Map(
    "binary" -> TopInfoSeq(),
    "chat" -> TopInfoSeq(),
    "all" -> TopInfoSeq()
  )

  override def receive: Receive = {
    case UserChannelJoined(connectionInfo, user, joinedPlace) =>
      // ignore chat
      if (user.inChannel == "Chat") {
        return receive
      }

      val topMapId =
        if (isChatProtocol(user.client)) {
          "chat"
        } else {
          "binary"
        }

      val topInfo = TopInfo(user, connectionInfo, joinedPlace)
      topMap(topMapId) += topInfo
      topMap("all") += topInfo

    case TopCommand(_, which) =>
      val topList = topMap(which)
      sender() ! UserInfo(TOP_INFO(topList.limit, which, Config().Server.host))
      topList
        .values
        .zipWithIndex
        .foreach {
          case (TopInfo(user, connectionInfo, joinedPlace), i) =>
            val client = connectionInfo.protocol match {
              case Chat1Protocol => "NEW CHAT"
              case TelnetProtocol => "OLD CHAT"
              case _ => user.client.reverse
            }
            sender() ! UserInfo(TOP_LIST(i + 1, user.name, client, connectionInfo.connectedTime, joinedPlace, user.inChannel))
        }
  }
}
