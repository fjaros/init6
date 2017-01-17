package com.init6.users

import java.util.concurrent.TimeUnit

import akka.actor.{ActorRef, PoisonPill, Props, Terminated}
import akka.io.Tcp.Received
import akka.pattern.ask
import akka.util.{ByteString, Timeout}
import com.init6.Constants._
import com.init6.coders.chat1.Chat1Encoder
import com.init6.coders.commands._
import com.init6.connection.WriteOut
import com.init6.{Config, Init6Actor, Init6LoggingActor, ReloadConfig}
import com.init6.channels._
import com.init6.channels.utils.ChannelJoinValidator
import com.init6.coders._
import com.init6.coders.binary.BinaryChatEncoder
import com.init6.coders.telnet._
import com.init6.db._
import com.init6.servers.{SendBirth, ServerOnline, SplitMe}
import com.init6.utils.CaseInsensitiveHashSet

import scala.concurrent.Await

/**
 * Created by filip on 9/27/15.
 */
object UserActor {
  def apply(connection: ActorRef, user: User, protocol: Protocol) = Props(classOf[UserActor], connection, user,
    protocol match {
      case BinaryProtocol => BinaryChatEncoder
      case TelnetProtocol => TelnetEncoder
      case Chat1Protocol => Chat1Encoder
  })
}

case class UserUpdated(user: User) extends Command
case class PingSent(time: Long, cookie: String) extends Command
case class UpdatePing(ping: Int) extends Command
case object KillConnection extends Command


class UserActor(connection: ActorRef, var user: User, encoder: Encoder)
  extends FloodPreventer with Init6Actor with Init6LoggingActor {

  var isTerminated = false
  var channelActor = ActorRef.noSender
  var squelchedUsers = CaseInsensitiveHashSet()
  val awayAvailablity = AwayAvailablity(user.name)
  val dndAvailablity = DndAvailablity(user.name)

  var pingTime: Long = 0
  var pingCookie: String = ""
  val connectedTime = System.currentTimeMillis()

  context.watch(connection)

  override def preStart() = {
    super.preStart()

    if (user.client == "TAHC") {
      joinChannel("Chat")
    }
  }

  def checkSquelched(user: User) = {
    if (squelchedUsers.contains(user.name)) {
      Flags.squelch(user)
    } else {
      Flags.unsquelch(user)
    }
  }

  def encodeAndSend(chatEvent: ChatEvent) = {
    encoder(chatEvent).foreach(message => connection ! WriteOut(message))
  }

  override def loggedReceive: Receive = {
    case ChannelToUserPing =>
      sender() ! UserToChannelPing

    // From Users Actor
    case UsersUserAdded(userActor, newUser) =>
      if (self != userActor && user.name.equalsIgnoreCase(newUser.name)) {
        // This user is a stale connection!
        self ! KillConnection
      }

    case GetUptime =>
      sender() ! ReceivedUptime(self, connectedTime)

    case PingSent(time, cookie) =>
      pingTime = time
      pingCookie = cookie

    case PongCommand(cookie) =>
      handlePingResponse(cookie)

    case c@ ChannelJoinResponse(event) =>
      event match {
        case UserChannel(newUser, channel, channelActor) =>
          this.channelActor = channelActor
          channelActor ! GetUsers
        case _ =>
      }
      encodeAndSend(event)

    case UserSquelched(username) =>
      squelchedUsers += username

    case UserUnsquelched(username) =>
      squelchedUsers -= username

    case UserUpdated(newUser) =>
      user = newUser

    case chatEvent: ChatEvent =>
      handleChatEvent(chatEvent)

    case (actor: ActorRef, WhisperMessage(fromUser, toUsername, message)) =>
      encoder(UserWhisperedFrom(fromUser, message))
        .foreach(msg => {
          dndAvailablity
            .whisperAction(actor)
            .getOrElse({
              connection ! WriteOut(msg)
              awayAvailablity.whisperAction(actor)
              actor ! UserWhisperedTo(user, message)
            })
        })

    case (actor: ActorRef, WhoisCommand(fromUser, username)) =>
      actor !
        (if (Flags.isAdmin(fromUser)) {
          UserInfo(s"${user.name} (${user.ipAddress}) is using ${encodeClient(user.client)}${if (user.inChannel != "") s" in the channel ${user.inChannel}" else ""} on server ${Config().Server.host}.")
        } else {
          UserInfo(s"${user.name} is using ${encodeClient(user.client)}${if (user.inChannel != "") s" in the channel ${user.inChannel}" else ""} on server ${Config().Server.host}.")
        })

    case (actor: ActorRef, PlaceOfUserCommand(_, _)) =>
      actor ! UserInfo(USER_PLACED(user.name, user.place, Config().Server.host))

    case WhoCommandResponse(whoResponseMessage, userMessages) =>
      whoResponseMessage.fold(encodeAndSend(UserErrorArray(CHANNEL_NOT_EXIST)))(whoResponseMessage => {
        encodeAndSend(UserInfo(whoResponseMessage))
        userMessages.foreach(userMessage => encodeAndSend(UserInfo(userMessage)))
      })

    case WhoCommandError(errorMessage) =>
      encodeAndSend(UserError(errorMessage))

    case c@ BanCommand(kicking, message) =>
      self ! UserInfo(YOU_KICKED(kicking))
      channelsActor ! UserSwitchedChat(self, user, THE_VOID)

    case KickCommand(kicking, message) =>
      self ! UserInfo(YOU_KICKED(kicking))
      channelsActor ! UserSwitchedChat(self, user, THE_VOID)

    case DAOCreatedAck(username, passwordHash) =>
      self ! UserInfo(ACCOUNT_CREATED(username, passwordHash))

    case DAOUpdatedPasswordAck(username, passwordHash) =>
      self ! UserInfo(ACCOUNT_UPDATED(username, passwordHash))

    case DAOClosedAccountAck(username, reason) =>
      self ! UserInfo(ACCOUNT_CLOSED(username, reason))

    case DAOOpenedAccountAck(username) =>
      self ! UserInfo(ACCOUNT_OPENED(username))

    // THIS SHIT NEEDS TO BE REFACTORED!
    case Received(data) =>
      // Handle AntiFlood
      if (Config().AntiFlood.enabled && floodState(data.length)) {
        encodeAndSend(UserFlooded)
        self ! KillConnection
      } else {
        CommandDecoder(user, data) match {
          case command: Command =>
            //log.error(s"UserMessageDecoder $command")
            command match {
              case PongCommand(cookie) =>
                handlePingResponse(cookie)

              /**
                * The channel command and user command have two different flows.
                * A user has to go through a middle-man users actor because there is no guarantee the receiving user is online.
                * A command being sent to the user's channel can be done via actor selection, since we can guarantee the
                * channel exists.
                */
              case c@JoinUserCommand(fromUser, channel) =>
                if (ChannelJoinValidator(user.inChannel, channel)) {
                  joinChannel(channel)
                }
              case ResignCommand => resign()
              case RejoinCommand => rejoin()
              case command: ChannelCommand =>
                if (channelActor != ActorRef.noSender) {
                  channelActor ! command
                }
              case ChannelsCommand => channelsActor ! ChannelsCommand
              case command: WhoCommand => channelsActor ! command
              case command: OperableCommand =>
                if (Flags.canBan(user)) {
                  usersActor ! command
                } else {
                  encoder(UserError(NOT_OPERATOR)).foreach(connection ! WriteOut(_))
                }
              case command: UserToChannelCommand => usersActor ! command
              case command: UserCommand => usersActor ! command
              case command: ReturnableCommand => encoder(command).foreach(connection ! WriteOut(_))
              case command@UsersCommand => usersActor ! command
              case command: TopCommand => topCommandActor ! command
              case AwayCommand(message) => awayAvailablity.enableAction(message)
              case DndCommand(message) => dndAvailablity.enableAction(message)
              case AccountMade(username, passwordHash) =>
                daoActor ! CreateAccount(username, passwordHash)
              case ChangePasswordCommand(newPassword) =>
                daoActor ! UpdateAccountPassword(user.name, newPassword)

              //ADMIN
              case SplitMe =>
                if (Flags.isAdmin(user)) {
//                  publish(TOPIC_SPLIT, SplitMe)
                }
              case SendBirth =>
                if (Flags.isAdmin(user)) {
//                  publish(TOPIC_ONLINE, ServerOnline)
                }
              case command @ BroadcastCommand(message) =>
                usersActor ! command
              case command @ DisconnectCommand(user) =>
                usersActor ! command
              case command @ CloseAccountCommand(account, reason) =>
                daoActor ! CloseAccount(account, reason)
              case command @ OpenAccountCommand(account) =>
                daoActor ! OpenAccount(account)
              case ReloadConfig =>
                Config.reload()
                self ! UserInfo(s"$INIT6 configuration reloaded.")
              case _ =>
            }
          case x =>
            log.debug("{} UserMessageDecoder Unhandled {}", user.name, x)
        }
      }

    case command: UserToChannelCommandAck =>
      //log.error(s"UTCCA $command")
      if (channelActor != ActorRef.noSender) {
        //println("Sending to channel UTCCA " + command)
        channelActor ! command
      }

    case Terminated(actor) =>
      //println("#TERMINATED " + sender() + " - " + self + " - " + user)
      // CAN'T DO THIS - channelActor msg might be faster than channelSActor join msg. might remove itself then add after
//      if (channelActor != ActorRef.noSender) {
//        channelActor ! RemUser(self)
//      } else {
        channelsActor ! RemUser(self)
//      }
      usersActor ! Rem(self)
      self ! PoisonPill

    case KillConnection =>
      //println("#KILLCONNECTION FROM " + sender() + " - FOR: " + self + " - " + user)
      connection ! PoisonPill

    case x =>
      log.debug("{} UserActor Unhandled {}", user.name, x)
  }

  private def handleChatEvent(chatEvent: ChatEvent) = {
    val chatEventSender = sender()
    // If it comes from Channels/*, make sure it is the current channelActor
    if (chatEventSender.path.parent.name != INIT6_CHANNELS_PATH || chatEventSender == channelActor) {
      chatEvent match {
        case UserIn(user) =>
          encodeAndSend(UserIn(checkSquelched(user)))

        case UserJoined(user) =>
          encodeAndSend(UserJoined(checkSquelched(user)))

        case UserFlags(user) =>
          encodeAndSend(UserFlags(checkSquelched(user)))

        case channelEvent: SquelchableTalkEvent =>
          if (!squelchedUsers.contains(channelEvent.user.name)) {
            encodeAndSend(channelEvent)
          }

        case _ =>
          encodeAndSend(chatEvent)
      }
    }
  }

  private def handlePingResponse(cookie: String) = {
    if (channelActor != ActorRef.noSender && pingCookie == cookie) {
      val updatedPing = Math.max(0, System.currentTimeMillis() - pingTime).toInt
      if (updatedPing <= 60000) {
        channelActor ! UpdatePing(updatedPing)
      }
    }
  }

  private def resign() = {
    if (Flags.isOp(user)) {
      rejoin()
    }
  }

  private def rejoin(): Unit = {
    joinChannel(user.inChannel)
  }

  private def joinChannel(channel: String) = {
    implicit val timeout = Timeout(5, TimeUnit.SECONDS)
    //println(user.name + " - " + self + " - SENDING JOIN")
    Await.result(channelsActor ? UserSwitchedChat(self, user, channel), timeout.duration) match {
      case ChannelJoinResponse(event) =>
        //println(user.name + " - " + self + " - RECEIVED JOIN")
        event match {
          case UserChannel(newUser, channel, channelActor) =>
            user = newUser
            this.channelActor = channelActor
            channelActor ! GetUsers
          case _ =>
        }
        encodeAndSend(event)
    }
  }
}
