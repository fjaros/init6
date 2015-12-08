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
sealed trait BinaryState
case object StartLoginState extends BinaryState
case object ExpectingSidStartVersioning extends BinaryState
case object ExpectingSidReportVersion extends BinaryState
case object ExpectingSidLogonChallenge extends BinaryState
case object ExpectingSidAuthInfo extends BinaryState
case object ExpectingSidAuthCheck extends BinaryState
case object ExpectingSidLogonResponse extends BinaryState
case object ExpectingSidEnterChat extends BinaryState
case object LoggedIn extends BinaryState

case class BinaryPacket(packetId: Byte, packet: ByteString)


object BinaryMessageHandler {
  def apply(clientAddress: InetSocketAddress, connection: ActorRef) = Props(new BinaryMessageHandler(clientAddress, connection))
}

class BinaryMessageHandler(clientAddress: InetSocketAddress, connection: ActorRef) extends ViLeNetActor with FSM[BinaryState, ActorRef] {

  implicit val timeout = Timeout(1, TimeUnit.MINUTES)

  startWith(StartLoginState, ActorRef.noSender)
  context.watch(connection)

  val pingCookie: Int = Random.nextInt
  val serverToken: Int = Random.nextInt

  var pingTime: Long = 0
  var ping: Int = -1

  var clientToken: Int = _
  var username: String = _
  var oldUsername: String = _
  var productId: String = _

  def handleRest(binaryPacket: BinaryPacket): State = {
    binaryPacket.packetId match {
      case SID_PING =>
        binaryPacket.packet match {
          case SidPing(packet) =>
            ping = Math.max(0, (System.currentTimeMillis() - pingTime).toInt)
        }
      case _ =>
    }
    stay()
  }

  def send(data: ByteString) = connection ! WriteOut(data)
  def sendPing() = {
    send(SidPing(pingCookie))
    pingTime = System.currentTimeMillis()
  }

  when(StartLoginState) {
    case Event(BinaryPacket(packetId, data), _) =>
      packetId match {
        case SID_CLIENTID =>
          send(SidLogonChallenge(serverToken))
          sendPing()
          send(SidStartVersioning())
          goto(ExpectingSidStartVersioning)
        case SID_CLIENTID2 =>
          send(SidLogonChallengeEx(serverToken))
          sendPing()
          send(SidStartVersioning())
          goto(ExpectingSidStartVersioning)
        case SID_AUTH_INFO =>
          data match {
            case SidAuthInfo(packet) =>
              productId = packet.productId
              println(productId)
              sendPing()
              send(SidAuthInfo(serverToken))
              goto(ExpectingSidAuthCheck)
            case _ => stop()
          }
      }
  }

  when(ExpectingSidStartVersioning) {
    case Event(BinaryPacket(packetId, data), _) =>
      packetId match {
        case SID_STARTVERSIONING =>
          data match {
            case SidStartVersioning(packet) =>
              productId = packet.productId
              goto(ExpectingSidReportVersion)
            case _ => stop()
          }
        case _ => handleRest(BinaryPacket(packetId, data))
      }
  }

  when(ExpectingSidReportVersion) {
    case Event(BinaryPacket(packetId, data), _) =>
      packetId match {
        case SID_REPORTVERSION =>
          data match {
            case SidReportVersion(packet) =>
              productId = packet.productId
              send(SidReportVersion(SidReportVersion.RESULT_SUCCESS))
              goto(ExpectingSidLogonResponse)
            case _ => stop()
          }
        case _ => handleRest(BinaryPacket(packetId, data))
      }
  }

  when(ExpectingSidLogonResponse) {
    case Event(BinaryPacket(packetId, data), _) =>
      packetId match {
        case SID_LOGONRESPONSE =>
          data match {
            case SidLogonResponse(packet) =>
              // Sanity Check!
//              if (serverToken == packet.serverToken) {
                handleLogon(packet.clientToken, packet.serverToken, packet.passwordHash, packet.username)
//              } else {
//                stop()
//              }
            case _ => stop()
          }
        case SID_LOGONRESPONSE2 =>
          data match {
            case SidLogonResponse2(packet) =>
              // Sanity Check!
              //if (serverToken == packet.serverToken) {
                handleLogon2(packet.clientToken, packet.serverToken, packet.passwordHash, packet.username)
//              } else {
//                stop()
//              }
            case _ => stop()
          }
        case _ => handleRest(BinaryPacket(packetId, data))
      }
  }

  def handleLogon(clientToken: Int, serverToken: Int, passwordHash: Array[Byte], username: String) = {
    oldUsername = username
    val u = User(oldUsername, 0, ping, client = productId)
    Await.result(usersActor ? Add(connection, u, BinaryProtocol), timeout.duration) match {
      case UsersUserAdded(userActor, user) =>
        this.username = user.name
        send(SidLogonResponse(SidLogonResponse.RESULT_SUCCESS))
        goto (ExpectingSidEnterChat) using userActor
      case _ => stop()
    }
  }

  def handleLogon2(clientToken: Int, serverToken: Int, passwordHash: Array[Byte], username: String) = {
    oldUsername = username
    val u = User(oldUsername, 0, ping, client = productId)
    Await.result(usersActor ? Add(connection, u, BinaryProtocol), timeout.duration) match {
      case UsersUserAdded(userActor, user) =>
        this.username = user.name
        send(SidLogonResponse2(SidLogonResponse2.RESULT_SUCCESS))
        goto (ExpectingSidEnterChat) using userActor
      case _ => stop()
    }
  }

  when(ExpectingSidAuthCheck) {
    case Event(BinaryPacket(packetId, data), _) =>
      packetId match {
        case SID_AUTH_CHECK =>
          data match {
            case SidAuthCheck(packet) =>
              clientToken = packet.clientToken
              send(SidAuthCheck())
              goto(ExpectingSidLogonResponse)
            case _ => stop()
          }
        case _ => handleRest(BinaryPacket(packetId, data))
      }
  }

  when(ExpectingSidEnterChat) {
    case Event(BinaryPacket(packetId, data), _) =>
      packetId match {
        case SID_ENTERCHAT =>
          data match {
            case SidEnterChat(packet) =>
//              if (oldUsername == packet.username) {
                send(SidEnterChat(username, oldUsername, productId))
                goto(LoggedIn)
//              } else {
//                stop()
//              }
          }
        case _ => handleRest(BinaryPacket(packetId, data))
      }
  }

  when(LoggedIn) {
    case Event(BinaryPacket(packetId, data), actor) =>
      packetId match {
        case SID_JOINCHANNEL =>
          data match {
            case SidJoinChannel(packet) =>
              send(BinaryChatEncoder(UserInfoArray(Constants.MOTD)).get)
              actor ! Received(ByteString(s"/j ${packet.channel}"))
              stay()
            case _ => stop()
          }
        case SID_CHATCOMMAND =>
          data match {
            case SidChatCommand(packet) =>
              actor ! Received(ByteString(packet.message))
              stay()
            case _ => stop()
          }
        case _ => stay()
      }
  }

  onTermination {
    case _ =>
      log.error("Connection stopped 4")
      context.stop(self)
  }
}
