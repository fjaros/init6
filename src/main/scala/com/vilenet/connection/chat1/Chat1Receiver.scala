package com.vilenet.connection.chat1

import java.net.InetSocketAddress
import java.util.concurrent.TimeUnit

import akka.actor.{FSM, Props, ActorRef}
import akka.io.Tcp.Received
import akka.pattern.ask
import akka.util.{ByteString, Timeout}
import com.vilenet.Config
import com.vilenet.Constants._
import com.vilenet.channels._
import com.vilenet.coders.binary.hash.BSHA1
import com.vilenet.coders.chat1.Chat1Encoder
import com.vilenet.connection._
import com.vilenet.db.DAO
import com.vilenet.users.{PingSent, Chat1Protocol, UsersUserAdded, Add}

import scala.annotation.switch
import scala.concurrent.Await

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
case object LoggedInChat1State extends Chat1State
case object JustLoggedInChat1

sealed trait Chat1Data
case class UserCredentials(username: String = "", alias: String = "", password: String = "", home: String = "") extends Chat1Data
case class LoggedInUser(actor: ActorRef, username: String) extends Chat1Data

class Chat1Handler(clientAddress: InetSocketAddress, connection: ActorRef) extends ViLeNetKeepAliveActor with FSM[Chat1State, Chat1Data] {

  implicit val timeout = Timeout(1, TimeUnit.MINUTES)

  startWith(LoggingInChat1State, UserCredentials())

  when (LoggingInChat1State) {
    case Event(Received(data), userCredentials: UserCredentials) =>
      val splt = data.utf8String.split(" ", 2)
      val (command, value) = (splt.head, splt.last)

      (command: @switch) match {
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
      connection ! WriteOut(Chat1Encoder(UserInfo(TELNET_CONNECTED(clientAddress.toString))).get)
      connection ! WriteOut(Chat1Encoder(UserInfoArray(Config.motd)).get)
      //keepAlive(buffer.actor, sendNull)
      stay()
    case Event(Received(data), loggedInUser: LoggedInUser) =>
      keptAlive = true
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
        connection ! WriteOut(Chat1Encoder(TELNET_INCORRECT_USERNAME))
        goto (BlockedInChat1State)
      })(dbUser => {
        if (BSHA1(userCredentials.password).sameElements(dbUser.passwordHash)) {
          val u = User(userCredentials.username, dbUser.flags | Flags.UDP, 0, client = "TAHC")
          Await.result(usersActor ? Add(connection, u, Chat1Protocol), timeout.duration) match {
            case UsersUserAdded(actor, user) =>
              connection ! WriteOut(Chat1Encoder(UserPing("ABCD")).get)
              actor ! PingSent(System.currentTimeMillis(), "ABCD")
              actor ! Received(ByteString(s"/j ${userCredentials.home}"))
              goto (LoggedInChat1State) using LoggedInUser(actor, userCredentials.username)
            case _ => stop()
          }
        } else {
          connection ! WriteOut(Chat1Encoder(TELNET_INCORRECT_PASSWORD))
          goto (BlockedInChat1State)
        }
      })
    } else {
      stop()
    }
  }

  onTransition {
    case LoggingInChat1State -> LoggedInChat1State =>
      self ! JustLoggedInChat1
  }
}
