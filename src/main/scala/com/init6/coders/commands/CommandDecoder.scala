package com.init6.coders.commands

import akka.util.ByteString
import com.init6.Constants._
import com.init6.ReloadConfig
import com.init6.channels.{Flags, User, UserError}
import com.init6.servers.{SendBirth, SplitMe}

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

object CommandDecoder {

  def apply(user: User, byteString: ByteString) = {
    if (byteString.head == '/') {
      val (command, message) = spanBySpace(byteString.tail)
      val userCommand = command.toLowerCase match {
        case "away" => AwayCommand(message)
        case "ban" => OneCommand(BanCommand(message), UserError(USER_NOT_LOGGED_ON))
        case "changepassword" | "chpass" => ChangePasswordCommand(message)
        case "channel" | "join" | "j" => OneCommand(JoinUserCommand(user, message), UserError(NO_CHANNEL_INPUT))
        case "channels" | "list" => ChannelsCommand
        case "designate" => OneCommand(DesignateCommand(user, message), UserError(INVALID_USER), UserError(ALREADY_OPERATOR))
        case "dnd" => DndCommand(message)
        case "emote" | "me" => OneCommand(EmoteCommand(user, message), EmptyCommand)
        case "help" | "?" => HelpCommand()
        case "kick" => OneCommand(KickCommand(message), UserError(USER_NOT_LOGGED_ON))
        case "makeaccount" | "createaccount" => MakeAccountCommand(message)
        case "motd" => MotdCommand()
        case "null" => EmptyCommand
        case "place" => PlaceCommand(user)
        case "pong" => PongCommand(message)
        case "rejoin" => RejoinCommand
        case "resign" => ResignCommand
        case "serveruptime" | "uptime" => UptimeCommand()
        case "serverversion" | "version" => VersionCommand()
        case "squelch" | "ignore" => SquelchCommand(user, message.takeWhile(_ != ' '))
        case "top" =>
          if (message.nonEmpty) {
            val lowerCaseMessage = message.toLowerCase
            lowerCaseMessage match {
              case "chat" | "binary" | "all" => TopCommand(lowerCaseMessage)
              case "" => TopCommand("all")
              case _ => UserError()
            }
          } else {
            TopCommand("all")
          }
        case "topic" => TopicCommand(user, message)
        case "unban" => OneCommand(UnbanCommand(message.takeWhile(_ != ' ')), UserError(USER_NOT_LOGGED_ON))
        case "unsquelch" | "unignore" => UnsquelchCommand(user, message.takeWhile(_ != ' '))
        case "users" => UsersCommand
        case "whisper" | "w" | "msg" | "m" => WhisperMessage(user, message)
        case "whoami" => WhoamiCommand(user)
        case "whois" | "whereis" => OneCommand(WhoisCommand(user, message), UserError(USER_NOT_LOGGED_ON), WhoamiCommand(user))
        case "who" => OneCommand(WhoCommand(user, message), WhoCommand(user, user.inChannel))
        //case "!bl!zzme!" => BlizzMe(user)
        case _ => UserError()
      }

      if (Flags.isAdmin(user)) {
        command.toLowerCase match {
          // Admin commands
          case "broadcast" | "bcast" => BroadcastCommand(message)
          case "closeaccount" => CloseAccountCommand(user, message)
          case "openaccount" => OpenAccountCommand(user, message)
          case "disconnect" | "dc" => DisconnectCommand(message)
          //case "splitme" => SplitMe
          //case "recon" => SendBirth
          case "reloadconfig" | "configreload" => ReloadConfig
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