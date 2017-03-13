package com.init6.connection.binary

import java.util.concurrent.TimeUnit

import akka.actor.{ActorRef, FSM, PoisonPill, Props}
import akka.io.Tcp.Received
import akka.util.{ByteString, Timeout}
import com.init6.Config
import com.init6.Constants._
import com.init6.channels.{User, UserInfoArray}
import com.init6.coders.binary.BinaryChatEncoder
import com.init6.coders.binary.hash.BSHA1
import com.init6.coders.binary.packets.Packets._
import com.init6.coders.binary.packets._
import com.init6.coders.commands.{FriendsList, PongCommand}
import com.init6.connection._
import com.init6.db.{CreateAccount, DAO, DAOCreatedAck, UpdateAccountPassword}
import com.init6.users._
import com.init6.utils.LimitedAction

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
case object ExpectingLogonHandled extends BinaryState
case object ExpectingLogon2Handled extends BinaryState
case object ExpectingChangePasswordHandled extends BinaryState
case object ExpectingSidCreateAccountFromDAO extends BinaryState
case object ExpectingSidCreateAccount2FromDAO extends BinaryState
case object LoggedIn extends BinaryState

case class BinaryPacket(packetId: Byte, packet: ByteString)


object BinaryMessageHandler {
  def apply(connectionInfo: ConnectionInfo) = Props(classOf[BinaryMessageHandler], connectionInfo)
}

class BinaryMessageHandler(connectionInfo: ConnectionInfo) extends Init6KeepAliveActor with FSM[BinaryState, ActorRef] {

  implicit val timeout = Timeout(500, TimeUnit.MILLISECONDS)

  val ALLOWED_PRODUCTS = Set("D2DV", "D2XP", "DRTL", "DSHR", "JSTR", "SEXP", "SSHR", "STAR", "W2BN")

  startWith(StartLoginState, ActorRef.noSender)
  context.watch(connectionInfo.actor)

  val pingCookie: Int = Random.nextInt
  val serverToken: Int = Random.nextInt
  val sidNullHandler = LimitedAction()

  var pingTime: Long = 0
  var ping: Int = -1

  var clientToken: Int = _
  var username: String = _
  var oldUsername: String = _
  var productId: String = _

  var actor: ActorRef = ActorRef.noSender


  def handleRest(binaryPacket: BinaryPacket): State = {
    log.debug(">> {} Received: {}", connectionInfo.actor, f"${binaryPacket.packetId}%X")
    binaryPacket.packetId match {
      case SID_NULL =>
        binaryPacket.packet match {
          case SidNull(packet) =>
            sidNullHandler.fold()(send(SidNull()))
        }
      case SID_PING =>
        binaryPacket.packet match {
          case SidPing(packet) =>
            val time = System.currentTimeMillis
            if (actor != ActorRef.noSender) {
              actor ! PongCommand(String.valueOf(packet.cookie))
            }
            if (ping == -1) {
              ping = if (pingCookie == packet.cookie) {
                Math.max(0, (time - pingTime).toInt)
              } else {
                0
              }
            }
          case x =>
            log.error(">> {} Unexpected ping packet: {}", connectionInfo.actor, x)
        }
      case SID_GETCHANNELLIST =>
        binaryPacket.packet match {
          case SidGetChannelList(packet) =>
            send(SidGetChannelList())
        }
      case SID_FRIENDSLIST =>
        binaryPacket.packet match {
          case SidFriendsList(packet) =>
            actor ! FriendsList()
        }
//      case SID_LEAVECHAT =>
//        binaryPacket.packet match {
//          case SidLeaveChat(packet) =>
//            //stateData ! UserLeftChat
//          case x => ////println(s"${x.getClass}")
//        }
      case packetId =>
        log.error(">> {} Unexpected: {}", connectionInfo.actor, f"$packetId%X")
    }
    stay()
  }

  def send(data: ByteString) = {
    if (log.isDebugEnabled) {
      val packetId = data.asByteBuffer.get(1)
      log.debug("<< {} {}", connectionInfo.actor, f"$packetId%X")
    }
    connectionInfo.actor ! WriteOut(data)
  }
  def sendPing() = {
    send(SidPing(pingCookie))
    pingTime = System.currentTimeMillis
    if (actor != ActorRef.noSender) {
      actor ! PingSent(pingTime, String.valueOf(pingCookie))
    }
  }

  def isAllowedProduct(productId: String) = {
    ALLOWED_PRODUCTS.contains(productId.reverse)
  }

  when(StartLoginState) {
    case Event(BinaryPacket(packetId, data), _) =>
      log.debug(">> {} Received: {}", connectionInfo.actor, f"$packetId%X")
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
              sendPing()
              send(SidAuthInfo(serverToken))
              goto(ExpectingSidAuthCheck)
            case _ => stop()
          }
        case _ => stop()
      }
  }

  when(ExpectingSidStartVersioning) {
    case Event(BinaryPacket(packetId, data), _) =>
      log.debug(">> {} Received: {}", connectionInfo.actor, f"$packetId%X")
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
      log.debug(">> {} Received: {}", connectionInfo.actor, f"$packetId%X")
      packetId match {
        case SID_REPORTVERSION =>
          data match {
            case SidReportVersion(packet) =>
              productId = packet.productId
              if (isAllowedProduct(productId)) {
                send(SidReportVersion(SidReportVersion.RESULT_SUCCESS))
                goto(ExpectingSidLogonResponse)
              } else {
                send(SidReportVersion(SidReportVersion.RESULT_FAILED_VERSION_CHECK))
                stop()
              }
            case _ => stop()
          }
        case _ => handleRest(BinaryPacket(packetId, data))
      }
  }

  when(ExpectingSidLogonResponse) {
    case Event(BinaryPacket(packetId, data), _) =>
      log.debug(">> Received: {}", f"$packetId%X")
      packetId match {
        case SID_CDKEY =>
          data match {
            case SidCdKey(packet) =>
              send(SidCdKey())
              stay()
            case _ => stop()
          }
        case SID_CDKEY2 =>
          data match {
            case SidCdKey2(packet) =>
              send(SidCdKey2())
              stay()
            case _ => stop()
          }
        case SID_LOGONRESPONSE =>
          data match {
            case SidLogonResponse(packet) =>
                handleLogon(packet.clientToken, packet.serverToken, packet.passwordHash, packet.username)
            case _ => stop()
          }
        case SID_LOGONRESPONSE2 =>
          data match {
            case SidLogonResponse2(packet) =>
                handleLogon2(packet.clientToken, packet.serverToken, packet.passwordHash, packet.username)
            case _ => stop()
          }
        case SID_CREATEACCOUNT =>
          data match {
            case SidCreateAccount(packet) =>
              createAccount(packet.passwordHash, packet.username)
            case _ => stop()
          }
        case SID_CREATEACCOUNT2 =>
          data match {
            case SidCreateAccount2(packet) =>
              createAccount2(packet.passwordHash, packet.username)
            case _ => stop()
          }
        case SID_CHANGEPASSWORD =>
          data match {
            case SidChangePassword(packet) =>
              changePassword(packet.clientToken, packet.serverToken, packet.oldPasswordHash, packet.newPasswordHash, packet.username)
            case _ => stop()
          }
        case _ => handleRest(BinaryPacket(packetId, data))
      }
    case _ => stay()
    //case x => //println(x) ; stay()
  }

  when(ExpectingSidCreateAccountFromDAO) {
    case Event(DAOCreatedAck(_, _), _) =>
      send(SidCreateAccount(SidCreateAccount.RESULT_ACCOUNT_CREATED))
      goto(ExpectingSidLogonResponse)
    case x =>
      log.debug(">> {} Unhandled in ExpectingSidCreateAccountFromDAO {}", connectionInfo.actor, x)
      stop()
  }

  when(ExpectingSidCreateAccount2FromDAO) {
    case Event(DAOCreatedAck(_, _), _) =>
      send(SidCreateAccount2(SidCreateAccount2.RESULT_ACCOUNT_CREATED))
      goto(ExpectingSidLogonResponse)
    case x =>
      log.debug(">> {} Unhandled in ExpectingSidCreateAccount2FromDAO {}", connectionInfo.actor, x)
      stop()
  }

  when(ExpectingLogonHandled) {
    case Event(UsersUserAdded(userActor, user), _) =>
      this.actor = userActor
      this.username = user.name
      send(SidLogonResponse(SidLogonResponse.RESULT_SUCCESS))
      goto(ExpectingSidEnterChat) using userActor
    case Event(UsersUserNotAdded(), _) =>
      stop()
    case x =>
      log.debug(">> {} Unhandled in ExpectingLogonHandled {}", connectionInfo.actor, x)
      stop()
  }

  when(ExpectingLogon2Handled) {
    case Event(UsersUserAdded(userActor, user), _) =>
      this.actor = userActor
      this.username = user.name
      send(SidLogonResponse2(SidLogonResponse2.RESULT_SUCCESS))
      goto(ExpectingSidEnterChat) using userActor
    case Event(UsersUserNotAdded(), _) =>
      stop()
    case x =>
      log.debug(">> {} Unhandled in ExpectingLogon2Handled {}", connectionInfo.actor, x)
      stop()
  }

  when(ExpectingChangePasswordHandled) {
    case Event(DAOCreatedAck(_, _), _) =>
      send(SidChangePassword(SidChangePassword.RESULT_SUCCESS))
      goto(ExpectingSidLogonResponse)
    case x =>
      log.debug(">> {} Unhandled in ExpectingChangePasswordHandled {}", connectionInfo.actor, x)
      stop()
  }

  def createAccount(passwordHash: Array[Byte], username: String): State = {
    if (username.length < Config().Accounts.minLength) {
      send(SidCreateAccount(SidCreateAccount.RESULT_FAILED))
      return goto(ExpectingSidLogonResponse)
    }

    username.foreach(c => {
      if (!Config().Accounts.allowedCharacters.contains(c.toLower)) {
        send(SidCreateAccount(SidCreateAccount.RESULT_FAILED))
        return goto(ExpectingSidLogonResponse)
      }
    })

    val maxLenUser = username.take(Config().Accounts.maxLength)
    DAO.getUser(maxLenUser).fold({
      daoActor ! CreateAccount(maxLenUser, passwordHash)
    })(dbUser => {
      send(SidCreateAccount(SidCreateAccount.RESULT_FAILED))
    })

    goto(ExpectingSidCreateAccountFromDAO)
  }

  def createAccount2(passwordHash: Array[Byte], username: String): State = {
    if (username.length < Config().Accounts.minLength) {
      send(SidCreateAccount2(SidCreateAccount2.RESULT_NAME_TOO_SHORT))
      return goto(ExpectingSidLogonResponse)
    }

    username.foreach(c => {
      if (!Config().Accounts.allowedCharacters.contains(c.toLower)) {
        send(SidCreateAccount2(SidCreateAccount2.RESULT_INVALID_CHARACTERS))
        return goto(ExpectingSidLogonResponse)
      }
    })

    val maxLenUser = username.take(Config().Accounts.maxLength)
    DAO.getUser(maxLenUser).fold({
      daoActor ! CreateAccount(maxLenUser, passwordHash)
    })(dbUser => {
      send(SidCreateAccount2(SidCreateAccount2.RESULT_ALREADY_EXISTS))
    })

    goto(ExpectingSidCreateAccount2FromDAO)
  }

  def handleLogon(clientToken: Int, serverToken: Int, passwordHash: Array[Byte], username: String) = {
    oldUsername = username
    DAO.getUser(username).fold({
      send(SidLogonResponse(SidLogonResponse.RESULT_INVALID_PASSWORD))
      goto(ExpectingSidLogonResponse)
    })(dbUser => {
      if (dbUser.closed) {
        send(SidLogonResponse(SidLogonResponse.RESULT_INVALID_PASSWORD))
        goto(ExpectingSidLogonResponse)
      } else {
        if (BSHA1(clientToken, serverToken, dbUser.password_hash).sameElements(passwordHash)) {
          val u = User(
            dbUser.id, dbUser.alias_id, connectionInfo.ipAddress.getAddress.getHostAddress, oldUsername,
            dbUser.flags, ping, client = productId
          )
          usersActor ! Add(connectionInfo, u, BinaryProtocol)
          goto(ExpectingLogonHandled)
        } else {
          send(SidLogonResponse(SidLogonResponse.RESULT_INVALID_PASSWORD))
          goto(ExpectingSidLogonResponse)
        }
      }
    })
  }

  def handleLogon2(clientToken: Int, serverToken: Int, passwordHash: Array[Byte], username: String) = {
    oldUsername = username
    DAO.getUser(username).fold({
      send(SidLogonResponse2(SidLogonResponse2.RESULT_DOES_NOT_EXIST))
      goto(ExpectingSidLogonResponse)
    })(dbUser => {
      if (dbUser.closed) {
        send(SidLogonResponse2(SidLogonResponse2.RESULT_ACCOUNT_CLOSED, dbUser.closed_reason))
        goto(ExpectingSidLogonResponse)
      } else {
        if (BSHA1(clientToken, serverToken, dbUser.password_hash).sameElements(passwordHash)) {
          val u = User(
            dbUser.id, dbUser.alias_id, connectionInfo.ipAddress.getAddress.getHostAddress, oldUsername,
            dbUser.flags, ping, client = productId
          )
          usersActor ! Add(connectionInfo, u, BinaryProtocol)
          goto(ExpectingLogon2Handled)
        } else {
          send(SidLogonResponse2(SidLogonResponse2.RESULT_INVALID_PASSWORD))
          goto(ExpectingSidLogonResponse)
        }
      }
    })
  }

  def changePassword(clientToken: Int, serverToken: Int, oldPasswordHash: Array[Byte], newPasswordHash: Array[Byte], username: String) = {
    DAO.getUser(username).fold({
      send(SidChangePassword(SidChangePassword.RESULT_FAILED))
      goto(ExpectingSidLogonResponse)
    })(dbUser => {
      if (BSHA1(clientToken, serverToken, dbUser.password_hash).sameElements(oldPasswordHash)) {
        daoActor ! UpdateAccountPassword(username, newPasswordHash)
        goto(ExpectingChangePasswordHandled)
      } else {
        send(SidChangePassword(SidChangePassword.RESULT_FAILED))
        goto(ExpectingSidLogonResponse)
      }
    })
  }

  when(ExpectingSidAuthCheck) {
    case Event(BinaryPacket(packetId, data), _) =>
      packetId match {
        case SID_AUTH_CHECK =>
          data match {
            case SidAuthCheck(packet) =>
              if (isAllowedProduct(productId)) {
                clientToken = packet.clientToken
                send(SidAuthCheck(SidAuthCheck.RESULT_SUCCESS))
                goto(ExpectingSidLogonResponse)
              } else {
                send(SidAuthCheck(SidAuthCheck.RESULT_INVALID_VERSION))
                stop()
              }
            case _ => stop()
          }
        case _ => handleRest(BinaryPacket(packetId, data))
      }
  }

  when(ExpectingSidEnterChat) {
    case Event(BinaryPacket(packetId, data), actor) =>
      log.debug(">> {} Received: {}", connectionInfo.actor, f"$packetId%X")
      packetId match {
        case SID_ENTERCHAT =>
          data match {
            case SidEnterChat(packet) =>
              send(SidEnterChat(username, oldUsername, productId))
              send(BinaryChatEncoder(UserInfoArray(Config().motd)).get)
              keepAlive(actor, () => sendPing())
              goto(LoggedIn)
          }
        case _ => handleRest(BinaryPacket(packetId, data))
      }
  }

  when(LoggedIn) {
    case Event(BinaryPacket(packetId, data), actor) =>
      log.debug(">> {} Received: {}", connectionInfo.actor, f"$packetId%X")
      keptAlive = 0
      packetId match {
        case SID_JOINCHANNEL =>
          data match {
            case SidJoinChannel(packet) =>
              // seems this isn't really good for our use case.
              // just always take the channel from packet.

              packet.joinFlag match {
                case 0x00 => send(SidChatEvent(0x0e, 0, 0, username, packet.channel))
                case 0x02 => actor ! JoinChannelFromConnection(packet.channel, true)
                case 0x01 | 0x05 => actor ! JoinChannelFromConnection(packet.channel, false)
                case _ =>
              }
              stay()
            case _ => stop()
          }
        case SID_CHATCOMMAND =>
          data match {
            case SidChatCommand(packet) =>
              actor ! Received(ByteString(packet.message, CHARSET))
              stay()
            case _ => stop()
          }
        case _ => handleRest(BinaryPacket(packetId, data))
      }
    case Event(WrittenOut, _) =>
      stay()
  }

  onTermination {
    case x =>
      log.debug(">> {} BinaryMessageHandler onTermination: {}", connectionInfo.actor, x)
      connectionInfo.actor ! PoisonPill
  }
}
