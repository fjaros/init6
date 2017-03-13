package com.init6.connection.chat1

import akka.actor.{ActorRef, FSM, PoisonPill, Props}
import akka.io.Tcp.Received
import akka.util.ByteString
import com.init6.Config
import com.init6.Constants._
import com.init6.channels._
import com.init6.coders.binary.hash.BSHA1
import com.init6.coders.chat1.Chat1Encoder
import com.init6.connection._
import com.init6.db.{CreateAccount, DAO, DAOCreatedAck}
import com.init6.users._

import scala.collection.mutable
import scala.util.Random

/**
  * Created by filip on 1/10/16.
  */
object Chat1Receiver {
  def apply(connectionInfo: ConnectionInfo) = Props(classOf[Chat1Receiver], connectionInfo)
}

class Chat1Receiver(override val connectionInfo: ConnectionInfo) extends ChatReceiver {

  override val handler = context.actorOf(Props(classOf[Chat1Handler], connectionInfo))
}

sealed trait Chat1State
case object LoggingInChat1State extends Chat1State
case object ExpectingCreateAccountResponse extends Chat1State
case object LoggedInChat1State extends Chat1State
case object JustLoggedInChat1
case object ExpectingAckOfLoginMessages extends Chat1State
case object StoreExtraData extends Chat1State

sealed trait Chat1Data
case class UserCredentials(username: String = "", alias: String = "", password: String = "", home: String = "", packetsToProcess: mutable.Buffer[ByteString] = mutable.Buffer.empty) extends Chat1Data
case class LoggedInUser(actor: ActorRef, userCredentials: UserCredentials) extends Chat1Data

class Chat1Handler(connectionInfo: ConnectionInfo) extends Init6KeepAliveActor with FSM[Chat1State, Chat1Data] {

  startWith(LoggingInChat1State, UserCredentials())

  when (LoggingInChat1State) {
    case Event(Received(data), userCredentials: UserCredentials) =>
      log.debug(">> {} Chat1 LoggingInChat1State", connectionInfo.actor)
      val splt = data.utf8String.split(" ", 2)
      val (command, value) = (splt.head, splt.last)

      command match {
        case "ACCT" => stay using userCredentials.copy(username = value)
        case "AS" => stay using userCredentials.copy(alias = value)
        case "PASS" => stay using userCredentials.copy(password = value)
        case "HOME" => stay using userCredentials.copy(home = value)
        case "LOGIN" => login(userCredentials)
        case _ => stop()
      }
  }

  when (LoggedInChat1State) {
    case Event(Received(data), loggedInUser: LoggedInUser) =>
      keptAlive = 0
      loggedInUser.actor ! Received(data)
      stay()
    case x => stay()
  }

  def login(userCredentials: UserCredentials) = {
    if (userCredentials.username.nonEmpty) {
      val alias =
        if (userCredentials.alias.nonEmpty) {
          userCredentials.alias
        } else {
          userCredentials.username
        }

      DAO.getUser(userCredentials.username).fold({
        createAccount(userCredentials)
      })(dbUser => {
        if (dbUser.closed) {
          connectionInfo.actor ! WriteOut(Chat1Encoder(LoginFailed(ACCOUNT_CLOSED(userCredentials.username, dbUser.closed_reason))).get)
          stop()
        } else {
          if (BSHA1(userCredentials.password).sameElements(dbUser.password_hash)) {
            val u = User(
              dbUser.id, dbUser.alias_id, connectionInfo.ipAddress.getAddress.getHostAddress, userCredentials.username,
              dbUser.flags | Flags.UDP, 0, client = "TAHC"
            )
            usersActor ! Add(connectionInfo, u, Chat1Protocol)
            goto(StoreExtraData) using userCredentials
          } else {
            connectionInfo.actor ! WriteOut(Chat1Encoder(LoginFailed(TELNET_INCORRECT_PASSWORD)).get)
            stop()
          }
        }
      })
    } else {
      stop()
    }
  }

  when (ExpectingCreateAccountResponse) {
    case Event(DAOCreatedAck(_, _), userCredentials: UserCredentials) =>
      login(userCredentials)
    case x =>
      log.debug(">> {} Unhandled in ExpectingCreateAccountResponse {}", connectionInfo.actor, x)
      stop()
  }

  when (ExpectingAckOfLoginMessages) {
    case Event(Received(data), loggedInUser: LoggedInUser) =>
      loggedInUser.userCredentials.packetsToProcess += data
      stay()
    case Event(WrittenOut, loggedInUser: LoggedInUser) =>
      sendPing(loggedInUser.actor)
      keepAlive(loggedInUser.actor, () => {
        sendPing(loggedInUser.actor)
      })
      loggedInUser.actor ! JoinChannelFromConnection(loggedInUser.userCredentials.home, forceJoin = true)
      loggedInUser.userCredentials.packetsToProcess.foreach(loggedInUser.actor ! Received(_))
      goto(LoggedInChat1State) using loggedInUser
  }

  when (StoreExtraData) {
    case Event(Received(data), buffer: UserCredentials) =>
      buffer.packetsToProcess += data
      stay()
    case Event(UsersUserAdded(actor, user), buffer: UserCredentials) =>
      val loggedInUser = LoggedInUser(actor, buffer)
      connectionInfo.actor ! WriteOut(Chat1Encoder(LoginOK).get)
      connectionInfo.actor ! WriteOut(Chat1Encoder(UserInfo(TELNET_CONNECTED(connectionInfo.ipAddress))).get)
      connectionInfo.actor ! WriteOut(Chat1Encoder(ServerTopicArray(Config().motd)).get)
      goto(ExpectingAckOfLoginMessages) using loggedInUser
    case Event(UsersUserNotAdded(), buffer: UserCredentials) =>
      connectionInfo.actor ! WriteOut(Chat1Encoder(LoginFailed(TELNET_TOO_MANY_LOGINS)).get)
      stop()
  }

  def createAccount(userCredentials: UserCredentials): State = {
    if (userCredentials.username.length < Config().Accounts.minLength) {
      send(LoginFailed(ACCOUNT_TOO_SHORT))
      return goto(LoggingInChat1State)
    }

    userCredentials.username.foreach(c => {
      if (!Config().Accounts.allowedCharacters.contains(c.toLower)) {
        send(LoginFailed(ACCOUNT_CONTAINS_ILLEGAL))
        return goto(LoggingInChat1State)
      }
    })

    val maxLenUser = userCredentials.username.take(Config().Accounts.maxLength)
    DAO.getUser(maxLenUser).fold({
      daoActor ! CreateAccount(maxLenUser, BSHA1(userCredentials.password))
    })(dbUser => {
      send(LoginFailed(ACCOUNT_ALREADY_EXISTS(maxLenUser)))
    })

    goto(ExpectingCreateAccountResponse) using userCredentials
  }

  def send(chatEvent: ChatEvent) = {
    Chat1Encoder(chatEvent).foreach(connectionInfo.actor ! WriteOut(_))
  }

  def sendPing(userActor: ActorRef) = {
    val pingCookie = (0 until 4).map(Random.alphanumeric).mkString

    connectionInfo.actor ! WriteOut(Chat1Encoder(UserPing(pingCookie)).get)
    userActor ! PingSent(System.currentTimeMillis(), pingCookie)
  }

  onTermination {
    case terminated =>
      connectionInfo.actor ! PoisonPill
  }
}
