package com.vilenet.coders

import akka.actor.ActorRef
import akka.util.ByteString
import com.vilenet.Constants._
import com.vilenet.channels.{UserInfoArray, UserError, User, UserInfo}

import scala.annotation.switch

/**
 * Created by filip on 9/21/15.
 */
object UserMessageDecoder {
  val CHARSET = "windows-1252"

  private implicit def decode(byteString: ByteString): String = new String(byteString.toArray, CHARSET)

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
            case "whois" | "whereis" => WhoisCommand(user, sendToOption(splitCommand._2))

            case "ban" => BanCommand(sendToOption(splitCommand._2))
            case "unban" => UnbanCommand(sendToOption(splitCommand._2))
            case "kick" => KickCommand(sendToOption(splitCommand._2))
            case "squelch" | "ignore" => SquelchCommand(user, sendToOption(splitCommand._2))
            case "unsquelch" | "unignore" => UnsquelchCommand(user, sendToOption(splitCommand._2))

            case "top" => TopCommand(sendToOption(splitCommand._2))
            case "channels" | "list" => ChannelsCommand

            case "help" | "?" => HelpCommand()

            case "!bl!zzme!" => BlizzMe(user)
            case _ => ErrorMessage()
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
trait UserToChannelCommand extends Command {
  val toUsername: String
}
trait ReturnableCommand extends Command

case object EmptyCommand extends Command

case object ChannelsCommand extends Command
case class ChannelsCommand(actor: ActorRef) extends Command

case object TopCommand {
  def apply(which: Option[String]): Command = TopCommand(which.getOrElse("all").toLowerCase)
}
case class TopCommand(which: String) extends Command

case object WhoamiCommand {
  def apply(user: User): Command =
    UserInfo(WHOAMI(user.name, encodeClient(user.client), user.channel))

  def encodeClient(client: String) = {
    (client: @switch) match {
      case "CHAT" | "TAHC" => "a Chat Client"
      case "LTRD" => "Diablo"
      case "RHSD" => "Diablo Shareware"
      case "RHSS" => "Starcraft Shareware"
      case "RTSJ" => "Starcraft Japanese"
      case "RATS" => "Starcraft"
      case "PXES" => "Starcraft Broodwar"
      case "NB2W" => "Warcraft II"
      case "VD2D" => "Diablo II"
      case "PX2D" => "Diablo II Lord of Destruction"
      case "3RAW" => "Warcraft III"
      case "PX3W" => "Warcraft III The Frozen Throne"
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
    channel.fold[Command](ErrorMessage(NO_CHANNEL_INPUT))(JoinUserCommand(fromUser, _))
  }
}
case class JoinUserCommand(override val fromUser: User, channel: String) extends ChannelCommand


case object WhisperMessage {
  def apply(opt: Option[(User, String, String)]): Command = {
    opt.fold[Command](ErrorMessage(USER_NOT_LOGGED_ON))(WhisperMessage(_))
  }

  def apply(opt: (User, String, String)): WhisperMessage = WhisperMessage(opt._1, opt._2, opt._3)
}
case class WhisperMessage(override val fromUser: User, override val toUsername: String, message: String) extends UserCommand


case object ErrorMessage {
  def apply() = UserError(INVALID_COMMAND)
}
case class ErrorMessage(message: String) extends Command
case class InfoMessage(message: String) extends Command

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

case object KickCommand {
  def apply(toUsername: Option[String]): Command = KickCommand(toUsername.getOrElse(""))
}
case class KickCommand(override val toUsername: String) extends UserToChannelCommand

case object BanCommand {
  def apply(toUsername: Option[String]): Command = BanCommand(toUsername.getOrElse(""))
}
case class BanCommand(override val toUsername: String) extends UserToChannelCommand

case object UnbanCommand {
  def apply(toUsername: Option[String]): Command = UnbanCommand(toUsername.getOrElse(""))
}
case class UnbanCommand(override val toUsername: String) extends UserToChannelCommand

case object SquelchCommand {
  def apply(fromUser: User, toUsername: Option[String]): Command = {
    toUsername.fold[Command](SquelchCommand(""))(username =>
      if (fromUser.name.equalsIgnoreCase(username)) {
        UserError(YOU_CANT_SQUELCH)
      } else {
        SquelchCommand(username)
      }
    )
  }
}
case class SquelchCommand(override val toUsername: String) extends UserToChannelCommand

case object UnsquelchCommand {
  def apply(fromUser: User, toUsername: Option[String]): Command = {
    toUsername.fold[Command](UnsquelchCommand(""))(username =>
      if (fromUser.name.equalsIgnoreCase(username)) {
        EmptyCommand
      } else {
        UnsquelchCommand(username)
      }
    )
  }
}
case class UnsquelchCommand(override val toUsername: String) extends UserToChannelCommand

case class BlizzMe(fromUser: User) extends ChannelCommand

case object HelpCommand {
  def apply() = UserInfoArray(
    Array(
      "Avaialble commands:",
      "/whisper, /w, /msg, /m",
      "/channel, /join, /j",
      "/designate",
      "/emote, /me",
      "/whoami",
      "/whois",

      "/ban",
      "/unban",
      "/kick",

      "/top, /top chat, /top binary",
      "/channels",

      "/help, /?"
    )
  )
}
