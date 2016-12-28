package com.vilenet.connection.chat1

import java.net.InetSocketAddress
import java.util.concurrent.TimeUnit

import akka.actor.{ActorRef, FSM, Props}
import akka.io.Tcp.Received
import akka.pattern.ask
import akka.util.{ByteString, Timeout}
import com.vilenet.{Config, ViLeNetClusterActor}
import com.vilenet.Constants._
import com.vilenet.channels._
import com.vilenet.coders.binary.hash.BSHA1
import com.vilenet.coders.chat1.Chat1Encoder
import com.vilenet.connection._
import com.vilenet.db.{CreateAccount, DAO, DAOCreatedAck}
import com.vilenet.users.{Add, Chat1Protocol, PingSent, UsersUserAdded}

import scala.concurrent.Await
import scala.util.Random

/**
  * Created by filip on 1/10/16.
  */
object Chat1Receiver {
  def apply(clientAddress: InetSocketAddress, connection: ActorRef) = Props(classOf[Chat1Receiver], clientAddress, connection)
}

class Chat1Receiver(clientAddress: InetSocketAddress, override val connection: ActorRef) extends ChatReceiver {

  override val handler = context.actorOf(Props(classOf[Chat1Handler], clientAddress, connection))
}

sealed trait Chat1State
case object LoggingInChat1State extends Chat1State
case object BlockedInChat1State extends Chat1State
case object ExpectingCreateAccountResponse extends Chat1State
case object LoggedInChat1State extends Chat1State
case object JustLoggedInChat1

sealed trait Chat1Data
case class UserCredentials(username: String = "", alias: String = "", password: String = "", home: String = "") extends Chat1Data
case class LoggedInUser(actor: ActorRef, username: String) extends Chat1Data

class Chat1Handler(clientAddress: InetSocketAddress, connection: ActorRef) extends ViLeNetClusterActor with ViLeNetKeepAliveActor with FSM[Chat1State, Chat1Data] {

  implicit val timeout = Timeout(1, TimeUnit.MINUTES)

  startWith(LoggingInChat1State, UserCredentials())

  when (LoggingInChat1State) {
    case Event(Received(data), userCredentials: UserCredentials) =>
      log.debug(">> {} Chat1 LoggingInChat1State", connection)
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
    case Event(JustLoggedInChat1, loggedInUser: LoggedInUser) =>
      log.debug(">> {} Chat1 LoggedInChat1State", connection)
      connection ! WriteOut(Chat1Encoder(UserInfo(TELNET_CONNECTED(clientAddress))).get)
      connection ! WriteOut(Chat1Encoder(UserInfoArray(Config.motd)).get)
      keepAlive(loggedInUser.actor, () => {
        sendPing(loggedInUser.actor)
      })
      stay()
    case Event(Received(data), loggedInUser: LoggedInUser) =>
      keptAlive = 0
      loggedInUser.actor ! Received(data)
      stay()
    case x => println(x) ; stay()
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
          connection ! WriteOut(Chat1Encoder(LoginFailed(ACCOUNT_CLOSED(userCredentials.username, dbUser.closedReason))).get)
          goto(BlockedInChat1State)
        } else {
          if (BSHA1(userCredentials.password).sameElements(dbUser.passwordHash)) {
            val u = User(userCredentials.username, dbUser.flags | Flags.UDP, 0, client = "TAHC")
            Await.result(usersActor ? Add(connection, u, Chat1Protocol), timeout.duration) match {
              case UsersUserAdded(actor, user) =>
                connection ! WriteOut(Chat1Encoder(LoginOK).get)
                sendPing(actor)
                actor ! Received(ByteString(s"/j ${userCredentials.home}"))
                goto(LoggedInChat1State) using LoggedInUser(actor, userCredentials.username)
              case _ => stop()
            }
          } else {
            connection ! WriteOut(Chat1Encoder(LoginFailed(TELNET_INCORRECT_PASSWORD)).get)
            goto(BlockedInChat1State)
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
      log.debug(">> {} Unhandled in ExpectingCreateAccountResponse {}", connection, x)
      stop()
  }

  def createAccount(userCredentials: UserCredentials): State = {
    if (userCredentials.username.length < Config.Accounts.minLength) {
      send(LoginFailed(ACCOUNT_TOO_SHORT))
      return goto(LoggingInChat1State)
    }

    userCredentials.username.foreach(c => {
      if (!Config.Accounts.allowedCharacters.contains(c.toLower)) {
        send(LoginFailed(ACCOUNT_CONTAINS_ILLEGAL))
        return goto(LoggingInChat1State)
      }
    })

    val maxLenUser = userCredentials.username.take(Config.Accounts.maxLength)
    DAO.getUser(maxLenUser).fold({
      publish(TOPIC_DAO, CreateAccount(maxLenUser, BSHA1(userCredentials.password)))
    })(dbUser => {
      send(LoginFailed(ACCOUNT_ALREADY_EXISTS(maxLenUser)))
    })

    goto(ExpectingCreateAccountResponse) using userCredentials
  }

  def send(chatEvent: ChatEvent) = {
    Chat1Encoder(chatEvent).foreach(connection ! WriteOut(_))
  }

  def sendPing(userActor: ActorRef) = {
    val pingCookie = (0 until 4).map(Random.alphanumeric).mkString

    connection ! WriteOut(Chat1Encoder(UserPing(pingCookie)).get)
    userActor ! PingSent(System.currentTimeMillis(), pingCookie)
  }

  onTransition {
    case LoggingInChat1State -> LoggedInChat1State =>
      self ! JustLoggedInChat1
    case x =>
      log.error("{} Chat1 onTransition unexpected state: {}", connection, x)
  }
}
