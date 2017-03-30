package com.init6.channels

import akka.actor.ActorRef
import com.init6.Constants._
import com.init6.coders.commands.{Command, ReturnableCommand}
import com.init6.servers.{NonRemotable, Remotable}

/**
 * Created by filip on 9/20/15.
 */
trait ChatEvent extends Command with Remotable
trait SquelchableTalkEvent extends ChatEvent {
  val user: User
}

// Battle.net events
case class UserIn(user: User) extends ChatEvent
case class UserJoined(user: User) extends ChatEvent
case class UserLeft(user: User) extends ChatEvent
case class UserWhisperedFrom(override val user: User, message: String) extends SquelchableTalkEvent
// ITalked only applicable to Init6 protocol
case class ITalked(user: User, message: String) extends ChatEvent
case class UserTalked(override val user: User, message: String) extends SquelchableTalkEvent
case class UserBroadcast(user: User, message: String) extends ChatEvent
case class UserChannel(user: User, channelName: String, channelFlags: Long, channelActor: ActorRef, channelSize: Int) extends ChatEvent
case class UserFlags(user: User) extends ChatEvent
case class UserWhisperedTo(user: User, message: String) extends ChatEvent
case class UserInfo(message: String) extends ChatEvent with ReturnableCommand
case class UserInfoArray(message: Array[String]) extends ChatEvent with ReturnableCommand
case class UserError(message: String = INVALID_COMMAND) extends ChatEvent with ReturnableCommand
case class UserErrorArray(message: Array[String]) extends ChatEvent with ReturnableCommand
case object UserNull extends ChatEvent
case class UserName(name: String) extends ChatEvent
case class UserPing(cookie: String) extends ChatEvent
case class UserEmote(override val user: User, message: String) extends SquelchableTalkEvent

// Internal actor events
case class UserSquelched(user: String) extends ChatEvent
case class UserUnsquelched(user: String) extends ChatEvent
case class UserSentChat(user: String, message: String) extends ChatEvent
case class UserSentEmote(user: String, message: String) extends ChatEvent
case class Designate(user: String, message: String) extends ChatEvent
case class UserSwitchedChat(actor: ActorRef, user: User, channel: String, connectionTimestamp: Long) extends ChatEvent with NonRemotable
case class UserLeftChat(user: User) extends ChatEvent
case object UserFlooded extends ChatEvent

// CHAT1 Specific
case object LoginOK extends ChatEvent
case class LoginFailed(reason: String) extends ChatEvent
case class ServerTopicArray(message: Array[String]) extends ChatEvent with ReturnableCommand
