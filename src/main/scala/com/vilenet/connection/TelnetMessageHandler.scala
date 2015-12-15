package com.vilenet.connection

import java.net.InetSocketAddress
import java.util.concurrent.TimeUnit

import akka.actor.{ActorRef, FSM, Props}
import akka.io.Tcp.{Close, Received}
import akka.pattern.ask
import akka.util.{Timeout, ByteString}
import com.vilenet.coders.binary.hash.BSHA1
import com.vilenet.db.DAO
import com.vilenet.{Constants, ViLeNetActor}
import com.vilenet.channels._
import com.vilenet.coders.telnet.TelnetEncoder
import com.vilenet.users.{UsersUserAdded, TelnetProtocol, Add}

import scala.collection.mutable.ArrayBuffer
import scala.concurrent.Await

sealed trait State
case object ExpectingUsername extends State
case object ExpectingPassword extends State
case object LoggedIn extends State

sealed trait Data
case object UnauthenticatedUser extends Data
case class UnauthenticatedUser(user: String) extends Data
case class AuthenticatedUser(user: User, actor: ActorRef) extends Data

case object JustLoggedIn extends ChatEvent

/**
 * Created by filip on 9/19/15.
 */
object TelnetMessageReceiver {
  def apply(clientAddress: InetSocketAddress, connection: ActorRef) = Props(new TelnetMessageReceiver(clientAddress, connection))
}

class TelnetMessageReceiver(clientAddress: InetSocketAddress, connection: ActorRef) extends ViLeNetActor {

  val handler = context.actorOf(TelnetMessageHandler(clientAddress, connection))
  val buffer = ArrayBuffer[Byte]()


  override def receive: Receive = {
    case Received(data) =>
      //println(data.utf8String.replace('\r','~').replace('\n','|'))
      val readData = data.takeWhile(b => b != '\r' && b != '\n')
      if (data.length == readData.length) {
        // Split packet
        buffer ++= readData
      } else {
        // End of packet found
        if (buffer.nonEmpty) {
          handler ! Received(ByteString(buffer.toArray[Byte] ++ readData.toArray[Byte]))
          buffer.clear()
        } else if (readData.nonEmpty) {
          handler ! Received(readData)
        }
      }
      val restOfData = data.drop(readData.length).dropWhile(b => b == '\r' || b == '\n')
      if (restOfData.nonEmpty) {
        receive(Received(restOfData))
      }
    case x =>
      println(s"Received $x and closing handler.")
      handler ! Close
  }
}

object TelnetMessageHandler {
  def apply(clientAddress: InetSocketAddress, connection: ActorRef) = Props(new TelnetMessageHandler(clientAddress, connection))
}

class TelnetMessageHandler(clientAddress: InetSocketAddress, connection: ActorRef) extends ViLeNetActor with FSM[State, Data] {

  implicit val timeout = Timeout(1, TimeUnit.MINUTES)

  startWith(ExpectingUsername, UnauthenticatedUser)
  context.watch(connection)

  when (ExpectingUsername) {
    case Event(Received(data), _) =>
      goto (ExpectingPassword) using UnauthenticatedUser(data.utf8String)
  }

  when (ExpectingPassword) {
    case Event(Received(data), buffer: UnauthenticatedUser) =>
//      DAO.getUser(buffer.user).fold({
//        log.error(s"User not found ${buffer.user}")
//        stop()
//      })(dbUser => {
//        log.error(s"User found ${buffer.user}")
//        if (BSHA1(data.toArray).sameElements(dbUser.passwordHash)) {
          val u = User(buffer.user, Flags.UDP, 0, client = "TAHC")
          Await.result(usersActor ? Add(connection, u, TelnetProtocol), timeout.duration) match {
            case UsersUserAdded(actor, user) => goto (LoggedIn) using AuthenticatedUser(user, actor)
            case x =>
              println(s"Stopped because $x")
              stop()
          }
//        } else {
//          log.error(s"Incorrect PW ${buffer.user} ${data.utf8String}")
//          stop()
//        }
//      })
    }

  when (LoggedIn) {
    case Event(JustLoggedIn, buffer: AuthenticatedUser) =>
      connection ! WriteOut(TelnetEncoder(s"ViLeNet Telnet Connection from [$clientAddress]"))
      connection ! WriteOut(TelnetEncoder(UserName(buffer.user.name)).get)
      connection ! WriteOut(TelnetEncoder(UserInfoArray(Constants.MOTD)).get)
      stay()
    case Event(Received(data), buffer: AuthenticatedUser) =>
      //log.error(s"Received $data")
      buffer.actor ! Received(data)
      stay()
  }

  whenUnhandled {
    case x =>
      log.error(s"Unhandled Event $x")
      stop()
  }

  onTransition {
    case ExpectingPassword -> LoggedIn =>
      self ! JustLoggedIn
  }

  onTermination {
    case x =>
      log.error(s"Connection stopped $x")
      context.stop(self)
  }
}
