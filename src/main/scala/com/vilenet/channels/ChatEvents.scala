package com.vilenet.channels

import akka.actor.ActorRef
import com.vilenet.Constants._
import com.vilenet.coders.{ReturnableCommand, Command}

/**
 * Created by filip on 9/20/15.
 */
trait ChatEvent extends Command
trait SquelchableTalkEvent extends ChatEvent {
  val user: User
}

case class UserIn(user: User) extends ChatEvent
case class UserJoined(user: User) extends ChatEvent
case class UserLeft(user: User) extends ChatEvent
case class UserWhisperedFrom(override val user: User, message: String) extends SquelchableTalkEvent
case class UserTalked(override val user: User, message: String) extends SquelchableTalkEvent
case class UserBroadcast(message: String) extends ChatEvent
case class UserChannel(user: User, channelName: String, channelActor: ActorRef) extends ChatEvent
case class UserFlags(user: User) extends ChatEvent
case class UserWhisperedTo(user: User, message: String) extends ChatEvent
case class UserInfo(message: String) extends ChatEvent with ReturnableCommand
case class UserInfoArray(message: Array[String]) extends ChatEvent with ReturnableCommand
case class UserError(message: String = INVALID_COMMAND) extends ChatEvent with ReturnableCommand
case class UserErrorArray(message: Array[String]) extends ChatEvent with ReturnableCommand
case object UserNull extends ChatEvent
case class UserName(name: String) extends ChatEvent
case class UserEmote(override val user: User, message: String) extends SquelchableTalkEvent

case class UserSquelched(user: String) extends ChatEvent
case class UserUnsquelched(user: String) extends ChatEvent
case class UserSentChat(user: String, message: String) extends ChatEvent
case class UserSentEmote(user: String, message: String) extends ChatEvent
case class Designate(user: String, mesasge: String) extends ChatEvent
case class UserSwitchedChat(actor: ActorRef, user: User, channel: String) extends ChatEvent
case class ChatEmptied(channel: String) extends ChatEvent