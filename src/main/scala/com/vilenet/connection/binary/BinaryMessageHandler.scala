package com.vilenet.connection.binary

import java.net.InetSocketAddress
import java.util.concurrent.TimeUnit

import akka.actor.{ActorRef, FSM, Props}
import akka.pattern.ask
import akka.io.Tcp.Received
import akka.util.{ByteString, Timeout}
import com.vilenet.channels.{User, UserInfoArray}
import com.vilenet.coders.binary.BinaryChatEncoder
import com.vilenet.coders.binary.packets._
import com.vilenet.coders.binary.packets.Packets._
import com.vilenet.connection._
import com.vilenet.users.{Add, BinaryProtocol, UsersUserAdded}
import com.vilenet.{Constants, ViLeNetActor}

import scala.concurrent.Await
import scala.util.Random

/**
  * Created by filip on 12/3/15.
  */
case class BinaryPacket(packetId: Byte, packet: Array[Byte])


object BinaryMessageHandler {
  def apply(clientAddress: InetSocketAddress, connection: ActorRef) = Props(new BinaryMessageHandler(clientAddress, connection))
}

class BinaryMessageHandler(clientAddress: InetSocketAddress, connection: ActorRef) extends ViLeNetActor with FSM[BinaryState, ActorRef] with DeBuffer {

  implicit val timeout = Timeout(1, TimeUnit.MINUTES)

  startWith(StartLoginState, ActorRef.noSender)
  context.watch(connection)

  val pingCookie: Int = Random.nextInt
  var pingTime: Long = 0
  var ping: Int = -1

  var clientToken: Int = _
  var username: String = _
  var oldUsername: String = _
  var productId: String = _

  def handleRest(): State = {
    case _ => stay()
  }

  when(StartLoginState) {
    case Event(BinaryPacket(packetId, data), _) =>
      packetId match {
        case SID_CLIENTID | SID_CLIENTID2 | SID_AUTH_INFO =>

        case _ =>
      }
      if (packetId == 0x05) {
        connection ! WriteOut(SidLogonChallenge())
        connection ! WriteOut(SidPing(pingCookie))
        pingTime = System.currentTimeMillis()
        connection ! WriteOut(SidStartVersioning())
        goto(ExpectingSidStartVersioning)
      } else if (packetId == 0x1E) {
        connection ! WriteOut(SidLogonChallengeEx())
        connection ! WriteOut(SidPing(pingCookie))
        pingTime = System.currentTimeMillis()
        connection ! WriteOut(SidStartVersioning())
        goto(ExpectingSidStartVersioning)
      } else if (packetId == 0x50) {
        productId = new String(data.slice(8, 12))
        connection ! WriteOut(SidPing(pingCookie))
        pingTime = System.currentTimeMillis()
        connection ! WriteOut(SidAuthInfo())
        goto(ExpectingSidAuthCheck)
      } else {
        handleRest()
      }
  }

  when(ExpectingSidStartVersioning) {
    case Event(BinaryPacket(packetId, data), _) =>
      if (packetId == 0x06) {
        productId = new String(data.slice(4, 8))
        goto(ExpectingSidReportVersion)
      } else {
        handleRest()
      }
  }

  when(ExpectingSidReportVersion) {
    case Event(BinaryPacket(packetId, data), _) =>
      if (packetId == 0x07) {
        connection ! WriteOut(SidReportVersion())
        goto(ExpectingSidLogonResponse)
      } else {
        handleRest()
      }
  }

  when(ExpectingSidLogonResponse) {
    case Event(BinaryPacket(packetId, data), _) =>
      if (packetId == 0x29 || packetId == 0x3A) {
        oldUsername = new String(data.drop(7*4).takeWhile(_ != 0))
        val u = User(oldUsername, 0, client = productId)
        Await.result(usersActor ? Add(connection, u, BinaryProtocol), timeout.duration) match {
          case UsersUserAdded(userActor, user) =>
            username = user.name
            connection ! WriteOut(SidLogonResponse2())
            goto (ExpectingSidEnterChat) using WithActor(userActor)
          case _ => stop()
        }
      } else {
        handleRest()
      }
  }

  when(ExpectingSidAuthInfo) {
    case Event(BinaryPacket(packetId, data), _) =>
      if (packetId == 0x50) {
        productId = new String(data.slice(8, 12))
        connection ! WriteOut(SidAuthInfo())
        goto(ExpectingSidAuthCheck)
      } else {
        handleRest()
      }
  }


  when(ExpectingSidAuthCheck) {
    case Event(BinaryPacket(packetId, data), _) =>
      if (packetId == 0x51) {
        clientToken = toDword(data)
        connection ! WriteOut(SidAuthCheck())
        goto(ExpectingSidLogonResponse)
      } else {
        handleRest()
      }
  }

  when(ExpectingSidEnterChat) {
    case Event(BinaryPacket(packetId, data), _) =>
      if (packetId == 0x0A) {
        connection ! WriteOut(SidEnterChat(username, oldUsername, productId))
        goto(ExpectingSidJoinChannel) using stateData
      } else {
        handleRest()
      }
  }

  when(ExpectingSidJoinChannel) {
    case Event(BinaryPacket(packetId, data), WithActor(actor)) =>
      if (packetId == 0x0C) {
        connection ! WriteOut(BinaryChatEncoder(UserInfoArray(Constants.MOTD)).get)
        actor ! Received(ByteString(s"/j ${new String(data.drop(4).takeWhile(_ != 0))}"))
      } else if (packetId == 0x0E) {
        actor ! Received(ByteString(data.slice(0, data.length - 1)))
      } else {
      }
      handleRest()
  }

  //  whenUnhandled {
  //    case Event(WithBinaryData(packetId, length, data), WithActor(actor)) =>
  //      if (packetId == 0x25) {
  //        ping = Math.max(0, (System.currentTimeMillis() - pingTime).toInt)
  //      }
  //      stay()
  //  }

  onTermination {
    case _ =>
      log.error("Connection stopped 4")
      context.stop(self)
  }
}
