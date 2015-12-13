package com.vilenet.coders

import akka.actor.ActorRef
import akka.util.ByteString
import com.vilenet.Constants._
import com.vilenet.SystemContext
import com.vilenet.channels._

import scala.annotation.switch

/**
 * Created by filip on 9/21/15.
 */
object UserMessageDecoder {

  private implicit def decode(byteString: ByteString): String = new String(byteString.toArray, CHARSET)

  def apply(user: User, byteString: ByteString) = {
    (byteString.head: @switch) match {
      case '/' =>
        splitToOption(byteString.tail).fold[Command](UserError())(splitCommand => {
          (splitCommand._1: @switch) match {
            case "whisper" | "w" | "msg" | "m" =>
              WhisperMessage(splitToOption(splitCommand._2).map(tuple => (user, tuple._1, tuple._2)))
            case "channel" | "join" | "j" => JoinUserCommand(user, sendToOption(splitCommand._2))
            case "designate" => DesignateCommand(user, sendToOption(splitCommand._2))
            case "emote" | "me" => EmoteMessage(user, sendToOption(splitCommand._2))
            case "whoami" => WhoamiCommand(user)
            case "whois" | "whereis" => WhoisCommand(user, sendToOption(splitCommand._2))
            case "who" => WhoCommand(user, sendToOption(splitCommand._2))

            case "ban" => BanCommand(sendToOption(splitCommand._2))
            case "unban" => UnbanCommand(sendToOption(splitCommand._2))
            case "kick" => KickCommand(sendToOption(splitCommand._2))
            case "squelch" | "ignore" => SquelchCommand(user, sendToOption(splitCommand._2))
            case "unsquelch" | "unignore" => UnsquelchCommand(user, sendToOption(splitCommand._2))
            case "away" => AwayCommand(sendToOption(splitCommand._2))
            case "dnd" => DndCommand(sendToOption(splitCommand._2))
            case "rejoin" => RejoinCommand // resign but doesnt care if ops
            case "resign" => ResignCommand // Only if ops

            case "top" => TopCommand(sendToOption(splitCommand._2))
            case "channels" | "list" => ChannelsCommand
            case "place" => PlaceCommand(user)
            case "motd" => MotdCommand()
            case "serveruptime" | "uptime" => UptimeCommand()

            case "help" | "?" => HelpCommand()

            case "!bl!zzme!" => BlizzMe(user)
            case _ => UserError()
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
trait OperableCommand extends Command
trait ReturnableCommand extends Command

case object EmptyCommand extends Command

case object ChannelsCommand extends Command
case class ChannelsCommand(actor: ActorRef) extends Command

case object TopCommand {
  def apply(which: Option[String]): Command = {
    val topOption = which.fold("all")(_.toLowerCase)
    topOption match {
      case "chat" | "binary" | "all" => TopCommand(topOption)
      case "" => TopCommand("all")
      case _ => UserError()
    }
  }
}
case class TopCommand(which: String) extends Command
object PlaceCommand extends ReturnableCommand {
  def apply(user: User): Command = UserInfo(PLACED(user.place))
}

case object WhoamiCommand {
  def apply(user: User): Command =
    UserInfo(WHOAMI(user.name, encodeClient(user.client), user.channel))
}
case class WhoamiCommand(message: String) extends ReturnableCommand

case object WhoisCommand extends Command {

  def apply(fromUser: User, username: Option[String]): Command = {
    username.fold[Command](UserError(USER_NOT_LOGGED_ON))(username => {
      if (fromUser.name.toLowerCase == username) {
        WhoamiCommand(fromUser)
      } else {
        WhoisCommand(fromUser, username)
      }
    })
  }
}
case class WhoisCommand(override val fromUser: User, override val toUsername: String) extends UserCommand

object AwayCommand {
  def apply(message: Option[String]): Command = AwayCommand(message.getOrElse(""))
}
case class AwayCommand(message: String) extends Command

object DndCommand {
  def apply(message: Option[String]): Command = DndCommand(message.getOrElse(""))
}
case class DndCommand(message: String) extends Command

object WhoCommand {
  def apply(fromUser: User, channel: Option[String]): Command = WhoCommand(fromUser, channel.getOrElse(fromUser.channel))
}
case class WhoCommand(fromUser: User, channel: String) extends Command
case class WhoCommandToChannel(actor: ActorRef, fromUser: User) extends Command

case object JoinUserCommand {
  def apply(fromUser: User, channel: Option[String]): Command = {
    channel.fold[Command](UserError(NO_CHANNEL_INPUT))(JoinUserCommand(fromUser, _))
  }
}
case class JoinUserCommand(override val fromUser: User, channel: String) extends ChannelCommand


case object WhisperMessage {
  def apply(opt: Option[(User, String, String)]): Command = {
    opt.fold[Command](UserError(USER_NOT_LOGGED_ON))(WhisperMessage(_))
  }

  def apply(opt: (User, String, String)): WhisperMessage = WhisperMessage(opt._1, opt._2, opt._3)
}
case class WhisperMessage(override val fromUser: User, override val toUsername: String, message: String) extends UserCommand


case object DesignateCommand {
  def apply(fromUser: User, designatee: Option[String]): Command = DesignateCommand(fromUser, designatee.getOrElse(""))
}
case class DesignateCommand(fromUser: User, override val toUsername: String) extends UserToChannelCommand with OperableCommand

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
case class KickCommand(override val toUsername: String) extends UserToChannelCommand with OperableCommand

case object BanCommand {
  def apply(toUsername: Option[String]): Command = BanCommand(toUsername.getOrElse(""))
}
case class BanCommand(override val toUsername: String) extends UserToChannelCommand with OperableCommand

case object UnbanCommand {
  def apply(toUsername: Option[String]): Command = UnbanCommand(toUsername.getOrElse(""))
}
case class UnbanCommand(override val toUsername: String) extends UserToChannelCommand with OperableCommand

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

case object RejoinCommand extends Command
case object ResignCommand extends Command

object UptimeCommand {
  def apply() = {
    val uptime = SystemContext.getUptime
    UserInfo(
      s"""Uptime: ${
        Array(
          uptime.toDays -> "day",
          uptime.toHours % 24 -> "hour",
          uptime.toMinutes % 60 -> "minute",
          uptime.toSeconds % 60 -> "second"
        )
          .filter(_._1 > 0)
          .map(tuple => s"${tuple._1} ${addS(tuple._1, tuple._2)}")
          .mkString(" ")
      }."""
    )
  }
}

case class BlizzMe(fromUser: User) extends ChannelCommand

object MotdCommand {
  def apply() = UserInfoArray(MOTD)
}

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
      "/away",
      "/dnd",

      "/ban",
      "/unban",
      "/kick",

      "/top, /top chat, /top binary",
      "/channels",
      "/motd",

      "/help, /?"
    )
  )
}
