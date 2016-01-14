package com.vilenet.users

import akka.actor.{PoisonPill, Terminated, ActorRef, Props}
import akka.io.Tcp.Received
import com.vilenet.Constants._
import com.vilenet.coders.chat1.Chat1Encoder
import com.vilenet.coders.commands._
import com.vilenet.connection.WriteOut
import com.vilenet.ViLeNetClusterActor
import com.vilenet.channels._
import com.vilenet.coders._
import com.vilenet.coders.binary.BinaryChatEncoder
import com.vilenet.coders.telnet._
import com.vilenet.db.{DAOAck, CreateAccount}
import com.vilenet.servers.{ServerOnline, SendBirth, SplitMe}
import com.vilenet.utils.CaseInsensitiveHashSet

/**
 * Created by filip on 9/27/15.
 */
object UserActor {
  def apply(connection: ActorRef, user: User, protocol: Protocol) = Props(new UserActor(connection, user,
    protocol match {
      case BinaryProtocol => BinaryChatEncoder
      case TelnetProtocol => TelnetEncoder
      case Chat1Protocol => Chat1Encoder
  }))
}

case class UserUpdated(user: User) extends Command
case class PingSent(time: Long, cookie: String) extends Command
case class UpdatePing(ping: Int) extends Command


class UserActor(connection: ActorRef, var user: User, encoder: Encoder) extends ViLeNetClusterActor {

  var channelActor: ActorRef = _
  var squelchedUsers = CaseInsensitiveHashSet()
  val awayAvailablity = AwayAvailablity(user.name)
  val dndAvailablity = DndAvailablity(user.name)

  var pingTime: Long = 0
  var pingCookie: String = ""

  context.watch(connection)

  def checkSquelched(user: User) = {
    if (squelchedUsers.contains(user.name)) {
      Flags.squelch(user)
    } else {
      Flags.unsquelch(user)
    }
  }

  def encodeAndSend(chatEvent: ChatEvent) = {
    encoder(chatEvent)
      .fold()(message => connection ! WriteOut(message))
  }

  override def receive: Receive = {
    case PingSent(time, cookie) =>
      pingTime = time
      pingCookie = cookie

    case PongCommand(cookie) =>
      handlePingResponse(cookie)

    case UserUpdated(newUser) =>
      user = newUser

    case UserSquelched(username) =>
      squelchedUsers += username

    case UserUnsquelched(username) =>
      squelchedUsers -= username

    case UserIn(user) =>
      encodeAndSend(UserIn(checkSquelched(user)))

    case UserJoined(user) =>
      encodeAndSend(UserJoined(checkSquelched(user)))

    case UserFlags(user) =>
      encodeAndSend(UserFlags(checkSquelched(user)))

    case UserLeftChat =>
      user = user.copy(channel = "")
      if (channelActor != ActorRef.noSender) {
        channelActor ! RemUser(self)
        channelActor = ActorRef.noSender
      }

    case channelEvent: SquelchableTalkEvent =>
      if (!squelchedUsers.contains(channelEvent.user.name)) {
        encodeAndSend(channelEvent)
      }

    case channelEvent: ChatEvent =>
      channelEvent match {
        case UserChannel(newUser, channel, channelActor) =>
          user = newUser
          this.channelActor = channelActor
        case _ =>
      }
      encodeAndSend(channelEvent)

    case (actor: ActorRef, WhisperMessage(fromUser, toUsername, message)) =>
      encoder(UserWhisperedFrom(fromUser, message))
        .fold()(msg => {
          dndAvailablity
            .whisperAction(actor)
            .getOrElse({
              connection ! WriteOut(msg)
              awayAvailablity.whisperAction(actor)
              actor ! UserWhisperedTo(user, message)
            })
        })

    case (actor: ActorRef, WhoisCommand(fromUser, username)) =>
      actor ! UserInfo(s"${user.name} is using ${encodeClient(user.client)}${if (user.channel != "") s" in the channel ${user.channel}" else ""}.")

    case BanCommand(kicking, message) =>
      self ! UserInfo(YOU_KICKED(kicking))
      channelsActor ! UserSwitchedChat(self, user, THE_VOID)

    case KickCommand(kicking, message) =>
      self ! UserInfo(YOU_KICKED(kicking))
      channelsActor ! UserSwitchedChat(self, user, THE_VOID)

    case DAOAck(username, passwordHash) =>
      self ! UserInfo(ACCOUNT_CREATED(username, passwordHash))

    case Received(data) =>
      CommandDecoder(user, data) match {
        case command: Command =>
          //log.error(s"UserMessageDecoder $command")
          command match {
            case PongCommand(cookie) =>
              handlePingResponse(cookie)

            /**
             * The channel command and user command have two different flows.
             *  A user has to go through a middle-man users actor because there is no guarantee the receiving user is online.
             *  A command being sent to the user's channel can be done via actor selection, since we can guarantee the
             *  channel exists.
             */
            case JoinUserCommand(fromUser, channel) =>
              if (!user.channel.equalsIgnoreCase(channel)) {
                channelsActor ! UserSwitchedChat(self, fromUser, channel)
              }
            case ResignCommand => resign()
            case RejoinCommand => rejoin()
            case command: ChannelCommand => channelActor ! command
            case ChannelsCommand => channelsActor ! ChannelsCommand
            case command: WhoCommand => channelsActor ! command
            case command: OperableCommand =>
              if (Flags.canBan(user)) {
                usersActor ! command
              } else {
                encoder(UserError(NOT_OPERATOR)).fold()(connection ! WriteOut(_))
              }
            case command: UserToChannelCommand => usersActor ! command
            case command: UserCommand => usersActor ! command
            case command: ReturnableCommand => encoder(command).fold()(connection ! WriteOut(_))
            case command @ UsersCommand => usersActor ! command
            case command: TopCommand => usersActor ! command
            case AwayCommand(message) => awayAvailablity.enableAction(message)
            case DndCommand(message) => dndAvailablity.enableAction(message)
            case AccountMade(username, passwordHash) =>
              publish(TOPIC_DAO, CreateAccount(username, passwordHash))
            case SplitMe =>
              if (Flags.isAdmin(user)) publish(TOPIC_SPLIT, SplitMe)
            case SendBirth =>
              if (Flags.isAdmin(user)) publish(TOPIC_ONLINE, ServerOnline)
            case command @ BroadcastCommand(message) =>
              usersActor ! command
            case _ =>
          }
        case x =>
          ////log.error(s"### UserMessageDecoder Unhandled: $x")
      }

    case command: UserToChannelCommandAck =>
      //log.error(s"UTCCA $command")
      channelActor ! command

    case Terminated(actor) =>
      publish(TOPIC_USERS, Rem(user.name))
      self ! PoisonPill

    case x =>
      ////log.error(s"### UserActor Unhandled: $x")
  }

  private def handlePingResponse(cookie: String) = {
    if (pingCookie == cookie) {
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

  private def rejoin() = {
    val oldChannel = user.channel
    channelActor ! RemUser(self)
    user = user.copy(channel = "")
    channelsActor ! UserSwitchedChat(self, user, oldChannel)
  }
}
