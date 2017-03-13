package com.init6.coders.commands

import akka.util.ByteString
import com.init6.Constants._
import com.init6.ReloadConfig
import com.init6.channels._
import com.init6.db.ReloadDb

/**
  * Created by filip on 9/21/15.
  */
object OneCommand {

  def apply(command: MessageCommand, errorCommand: Command) = {
    if (command.message.nonEmpty) {
      command
    } else {
      errorCommand
    }
  }

  def apply(command: UserToChannelCommand, errorCommand: Command) = {
    if (command.toUsername.nonEmpty) {
      command
    } else {
      errorCommand
    }
  }

  def apply(command: UserCommand, errorCommand: Command, ifEqualErrorCommand: Command) = {
    if (command.toUsername.nonEmpty) {
      if (command.fromUser.name.equalsIgnoreCase(command.toUsername)) {
        ifEqualErrorCommand
      } else {
        command
      }
    } else {
      errorCommand
    }
  }
}

object OneOperableCommand {

  def apply(user: User, command: MessageCommand, errorCommand: Command) = {
    if (Flags.canBan(user)) {
      OneCommand(command, errorCommand)
    } else {
      UserError(NOT_OPERATOR)
    }
  }

  def apply(user: User, command: UserToChannelCommand, errorCommand: Command) = {
    if (Flags.canBan(user)) {
      OneCommand(command, errorCommand)
    } else {
      UserError(NOT_OPERATOR)
    }
  }

  def apply(user: User, command: UserCommand, errorCommand: Command, ifEqualErrorCommand: Command) = {
    if (Flags.canBan(user)) {
      OneCommand(command, errorCommand, ifEqualErrorCommand)
    } else {
      UserError(NOT_OPERATOR)
    }
  }
}

object CommandDecoder {

  def apply(user: User, byteString: ByteString): Command = {
    if (byteString.head == '/') {
      val (command, message) = spanBySpace(byteString.tail)
      val userCommand = command.toLowerCase match {
        case "alias" | "register" => AliasCommand(user, message)
        case "aliasto" | "registerto" => AliasToCommand(user, message)
        case "away" => AwayCommand(message)
        case "ban" => OneOperableCommand(user, BanCommand(message), UserError(USER_NOT_LOGGED_ON))
        case "changepassword" | "chpass" => ChangePasswordCommand(message)
        case "channel" | "join" | "j" => OneCommand(JoinUserCommand(user, message), UserError(NO_CHANNEL_INPUT))
        case "channels" | "chs" | "list" => ChannelsCommand
        case "designate" => OneOperableCommand(user, DesignateCommand(user, message), UserError(INVALID_USER), UserError(ALREADY_OPERATOR))
        case "dnd" => DndCommand(message)
        case "emote" | "me" => OneCommand(EmoteCommand(user, message), EmptyCommand)
        case "friends" | "f" => FriendsCommand(message)
        case "serverhelp" | "help" | "?" => HelpCommand()
        case "kick" => OneOperableCommand(user, KickCommand(message), UserError(USER_NOT_LOGGED_ON))
        case "makeaccount" | "createaccount" => MakeAccountCommand(message)
        case "servermotd" | "motd" => MotdCommand()
        case "null" => EmptyCommand
        case "ops" => OneCommand(WhoCommand(user, message, opsOnly = true), WhoCommand(user, user.inChannel, opsOnly = true))
        case "place" => PlaceCommand(user, message)
        case "pong" => PongCommand(message)
        case "rejoin" | "rj" => RejoinCommand
        case "resign" => ResignCommand
        case "serveruptime" | "uptime" => UptimeCommand
        case "serverversion" | "version" => VersionCommand()
        case "squelch" | "ignore" => SquelchCommand(user, message.takeWhile(_ != ' '))
        case "top" => TopCommand(message)
        case "topic" => TopicCommand(user, message)
        case "unban" => OneCommand(UnbanCommand(message.takeWhile(_ != ' ')), UserError(USER_NOT_LOGGED_ON))
        case "unsquelch" | "unignore" => UnsquelchCommand(user, message.takeWhile(_ != ' '))
        case "users" => UsersCommand
        case "whisper" | "w" | "msg" | "m" => WhisperMessage(user, message)
        case "whoami" => WhoamiCommand(user)
        case "whois" | "whereis" => OneCommand(WhoisCommand(user, message), UserError(USER_NOT_LOGGED_ON), WhoamiCommand(user))
        case "who" => OneCommand(WhoCommand(user, message, opsOnly = false), WhoCommand(user, user.inChannel, opsOnly = false))
        //case "!bl!zzme!" => BlizzMe(user)
        case _ => UserError()
      }

      if (Flags.isAdmin(user)) {
        command.toLowerCase match {
          // Admin commands
          case "broadcast" | "bcast" => BroadcastCommand(message)
          case "diabot" => StartRP
          case "quitdiabot" => EndRP
          case "closeaccount" => CloseAccountCommand(user, message)
          case "openaccount" => OpenAccountCommand(user, message)
          case "disconnect" | "dc" => DisconnectCommand(message)
          case "ipban" => IpBanCommand(message)
          case "unipban" => UnIpBanCommand(message)
          //case "splitme" => SplitMe
          //case "recon" => SendBirth
          case "printchannelusers" => OneCommand(PrintChannelUsers(message), PrintChannelUsers(user.inChannel))
          case "printconnectionlimit" => PrintConnectionLimit
          case "printloginlimit" => PrintLoginLimit
          case "reloadconfig" | "configreload" => ReloadConfig
          case "reloaddb" => ReloadDb
          case "showchannelbans" => ShowChannelBans(message)
          case "showuserbans" => ShowUserBans(message)
          case "usermute" | "muteuser" => UserMute(message)
          case "userunmute" | "unmuteuser" => UserUnmute(message)
          case _ => userCommand
        }
      } else {
        userCommand
      }
    } else {
      OneCommand(ChatCommand(user, byteString.take(250)), EmptyCommand)
    }
  }

  private implicit def decode(byteString: ByteString): String = new String(byteString.toArray, CHARSET)

  private[commands] def spanBySpace(string: String) = {
    val splt = string.span(_ != ' ')
    if (splt._2.nonEmpty) {
      splt.copy(_2 = splt._2.drop(1))
    } else {
      splt
    }
  }
}
