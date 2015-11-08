package com.vilenet.coders.telnet

import akka.util.ByteString
import com.vilenet.Constants
import com.vilenet.channels.{ChatEvent, UserInfo, User}
import Constants.USER_NOT_LOGGED_ON

import scala.annotation.switch

/**
 * Created by filip on 9/21/15.
 */
object UserMessageDecoder {

  implicit def decode(byteString: ByteString): String = byteString.utf8String

  def apply(user: User, byteString: ByteString) = {
    (byteString.head: @switch) match {
      case '/' =>
        splitToOption(byteString.tail).fold[Command](ErrorMessage())(splitCommand => {
          (splitCommand._1: @switch) match {
            case "whisper" | "w" | "msg" | "m" =>
              WhisperMessage(splitToOption(splitCommand._2).map(tuple => (user, tuple._1, tuple._2)))
            case "channel" | "join" | "j" => JoinUserCommand(user, sendToOption(splitCommand._2))
            case "designate" => DesignateCommand(user, sendToOption(splitCommand._2))
            case "emote" | "me" => EmoteMessage(user, sendToOption(splitCommand._2))
            case "whoami" => WhoamiCommand(user)
            case "whois" => WhoisCommand(user, sendToOption(splitCommand._2))
            case _ => EmptyCommand
          }
        })
      case _ =>
        ChatMessage(user, byteString.take(250))
    }
  }

  def sendToOption(message: String): Option[String] = {
    if (message.nonEmpty) {
      Some(message)
    } else {
      None
    }
  }

  def splitToOption(messageToSplit: String): Option[(String, String)] = {
    if (messageToSplit.nonEmpty) {
      val splt = messageToSplit.split(" ", 2)
      Some((splt(0).toLowerCase, if (splt.length == 2) splt(1) else ""))
    } else {
      None
    }
  }
}

/**
 * Commands have 4 types.
 *  ChannelCommand where a user is interacting with a channel - shall be passed to a Channel Actor
 *  UserCommand where a user is interacting with another user - shall be passed to Users Actor
 *  ReturnableCommand where we already know enough and return directly to the user.
 * Commands can also be general (e.g. ErrorCommand which can be returned from a user or a channel)
 */

trait Command
trait ChannelCommand extends Command {
  val fromUser: User
}
trait UserCommand extends Command {
  val fromUser: User
  val toUsername: String
}
trait UserToChannelCommand extends UserCommand
trait ReturnableCommand extends Command

case object EmptyCommand extends Command

case object WhoamiCommand {
  def apply(user: User): Command =
    UserInfo(s"You are ${user.name}, using ${encodeClient(user.client)} in the channel ${user.channel}.")

  def encodeClient(client: String) = {
    (client: @switch) match {
      case "CHAT" => "Chat"
      case _ => "Unknown"
    }
  }
}
case class WhoamiCommand(message: String) extends ReturnableCommand

case object WhoisCommand extends Command {

  def apply(fromUser: User, username: Option[String]): Command = {
    username.fold[Command](ErrorMessage(USER_NOT_LOGGED_ON))(username => {
      if (fromUser.name.toLowerCase == username) {
        WhoamiCommand(fromUser)
      } else {
        WhoisCommand(fromUser, username)
      }
    })
  }
}
case class WhoisCommand(override val fromUser: User, override val toUsername: String) extends UserCommand

case object JoinUserCommand {
  def apply(fromUser: User, channel: Option[String]): Command = {
    channel.fold[Command](ErrorMessage("What channel do you want to join?"))(JoinUserCommand(fromUser, _))
  }
}
case class JoinUserCommand(override val fromUser: User, channel: String) extends ChannelCommand


case object WhisperMessage {
  def apply(opt: Option[(User, String, String)]): Command = {
    opt.fold[Command](ErrorMessage("That user is not logged on."))(WhisperMessage(_))
  }

  def apply(opt: (User, String, String)): WhisperMessage = WhisperMessage(opt._1, opt._2, opt._3)
}
case class WhisperMessage(override val fromUser: User, override val toUsername: String, message: String) extends UserCommand


case object ErrorMessage {
  def apply(): ErrorMessage = ErrorMessage("That is not a valid command. Type /help or /? for more info.")
}
case class ErrorMessage(message: String) extends Command

case object DesignateCommand {
  def apply(fromUser: User, designatee: Option[String]): Command = DesignateCommand(fromUser, designatee.getOrElse(""))
}
case class DesignateCommand(fromUser: User, override val toUsername: String) extends UserToChannelCommand

case class ChatMessage(fromUser: User, message: String) extends ChannelCommand
case object EmoteMessage {
  def apply(fromUser: User, message: Option[String]): Command = {
    message.fold[Command](EmptyCommand)(EmoteMessage(fromUser, _))
  }
}
case class EmoteMessage(fromUser: User, message: String) extends ChannelCommand