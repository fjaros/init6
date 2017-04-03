package com.init6.connection

import akka.actor.{ActorRef, FSM, PoisonPill, Props}
import akka.io.Tcp.Received
import akka.util.ByteString
import com.init6.Config
import com.init6.Constants._
import com.init6.channels._
import com.init6.coders.binary.hash.BSHA1
import com.init6.coders.telnet.TelnetEncoder
import com.init6.db.DAO
import com.init6.users._

import scala.collection.mutable

sealed trait State
case object ExpectingUsername extends State
case object ExpectingPassword extends State
case object LoggedIn extends State
case object ExpectingAckOfLoginMessages extends State
case object StoreExtraData extends State

sealed trait Data
case object UnauthenticatedUser extends Data
case class UnauthenticatedUser(user: String, packetsToProcess: mutable.Buffer[ByteString] = mutable.Buffer.empty) extends Data
case class AuthenticatedUser(actor: ActorRef, user: User, packetsToProcess: mutable.Buffer[ByteString]) extends Data

/**
 * Created by filip on 9/19/15.
 */
//object TelnetMessageReceiver {
//  def apply(connectionInfo: ConnectionInfo) = Props(classOf[TelnetMessageReceiver], connectionInfo)
//}
//
//class TelnetMessageReceiver(override val connectionInfo: ConnectionInfo) extends ChatReceiver {
//
//  override val handler = context.actorOf(TelnetMessageHandler(connectionInfo))
//}

object TelnetMessageHandler {
  def apply(connectionInfo: ConnectionInfo) = Props(classOf[TelnetMessageHandler], connectionInfo)
}

class TelnetMessageHandler(connectionInfo: ConnectionInfo)
  extends Init6KeepAliveActor with FSM[State, Data] {

  override def preStart() = {
    super.preStart()

    context.watch(connectionInfo.actor)
    startWith(ExpectingUsername, UnauthenticatedUser)
  }

  def sendNull() = {
    connectionInfo.actor ! WriteOut(TelnetEncoder(UserNull).get)
  }

  when (ExpectingUsername) {
    case Event(data: ByteString, _) =>
      goto(ExpectingPassword) using UnauthenticatedUser(data.utf8String)
  }

  when (ExpectingPassword) {
    case Event(data: ByteString, buffer: UnauthenticatedUser) =>
      DAO.getUser(buffer.user).fold({
        connectionInfo.actor ! WriteOut(TelnetEncoder(TELNET_INCORRECT_USERNAME))
        stop()
      })(dbUser => {
        if (dbUser.closed) {
          connectionInfo.actor ! WriteOut(TelnetEncoder(ACCOUNT_CLOSED(buffer.user, dbUser.closed_reason)))
          stop()
        } else {
          if (BSHA1(data.toArray).sameElements(dbUser.password_hash)) {
            val u = User(
              dbUser.id, dbUser.alias_id, connectionInfo.ipAddress.getAddress.getHostAddress, buffer.user,
              dbUser.flags | Flags.UDP, 0, client = "TAHC"
            )
            usersActor ! Add(connectionInfo, u, TelnetProtocol)
            goto(StoreExtraData)
          } else {
            connectionInfo.actor ! WriteOut(TelnetEncoder(TELNET_INCORRECT_PASSWORD))
            stop()
          }
        }
      })
  }

  when (StoreExtraData) {
    case Event(data: ByteString, buffer: UnauthenticatedUser) =>
      buffer.packetsToProcess += data
      stay()
    case Event(UsersUserAdded(actor, user), buffer: UnauthenticatedUser) =>
      val authenticatedUser = AuthenticatedUser(actor, user, buffer.packetsToProcess)
      handleLoggedIn(authenticatedUser)
      goto(ExpectingAckOfLoginMessages) using authenticatedUser
    case Event(UsersUserNotAdded(), buffer: UnauthenticatedUser) =>
      connectionInfo.actor ! WriteOut(TelnetEncoder(TELNET_TOO_MANY_LOGINS))
      stop()
  }

  when (LoggedIn) {
    case Event(data: ByteString, buffer: AuthenticatedUser) =>
      keptAlive = 0
      buffer.actor ! Received(data)
      stay()
  }

  when (ExpectingAckOfLoginMessages) {
    case Event(data: ByteString, buffer: AuthenticatedUser) =>
      buffer.packetsToProcess += data
      stay()
    case Event(WrittenOut, buffer: AuthenticatedUser) =>
      buffer.actor ! JoinChannelFromConnection({
        if (Config().Server.Chat.enabled) {
          THE_VOID
        } else {
          "Chat"
        }
      }, false)
      buffer.packetsToProcess.foreach(buffer.actor ! Received(_))
      goto(LoggedIn)
  }

  def handleLoggedIn(buffer: AuthenticatedUser) = {
    connectionInfo.actor ! WriteOut(TelnetEncoder(TELNET_CONNECTED(connectionInfo.ipAddress)))
    connectionInfo.actor ! WriteOut(TelnetEncoder(UserName(buffer.user.name)).get)
    connectionInfo.actor ! WriteOut(TelnetEncoder(UserInfoArray(Config().motd)).get)
  }

  onTermination {
    case terminated =>
      connectionInfo.actor ! PoisonPill
  }
}
