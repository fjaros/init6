package com.vilenet.users

import java.util.concurrent.TimeUnit

import akka.actor.{ActorRef, PoisonPill, Props, Terminated}
import akka.cluster.pubsub.DistributedPubSubMediator
import akka.io.Tcp.Received
import akka.pattern.ask
import akka.util.{ByteString, Timeout}
import com.vilenet.Constants._
import com.vilenet.coders.chat1.Chat1Encoder
import com.vilenet.coders.commands._
import com.vilenet.connection.WriteOut
import com.vilenet.{Config, ViLeNetClusterActor}
import com.vilenet.channels._
import com.vilenet.coders._
import com.vilenet.coders.binary.BinaryChatEncoder
import com.vilenet.coders.telnet._
import com.vilenet.db._
import com.vilenet.servers.{SendBirth, ServerOnline, SplitMe}
import com.vilenet.utils.CaseInsensitiveHashSet

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
  extends FloodPreventer with ViLeNetClusterActor {

  var isTerminated = false
  var channelTopic: Option[String] = None
  var squelchedUsers = CaseInsensitiveHashSet()
  val awayAvailablity = AwayAvailablity(user.name)
  val dndAvailablity = DndAvailablity(user.name)

  var pingTime: Long = 0
  var pingCookie: String = ""
  val connectedTime = System.currentTimeMillis()

  context.watch(connection)

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

  override def receive: Receive = {
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

    case command @ UserChannel(newUser, channel, channelActor) =>
      channelTopic = Some(TOPIC_CHANNEL(channel))
      channelActor ! GetUsers
      user = newUser
      encodeAndSend(command)

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
      channelTopic.foreach(publish(RemUser(self)))
      channelTopic = None

    case channelEvent: SquelchableTalkEvent =>
      if (!squelchedUsers.contains(channelEvent.user.name)) {
        encodeAndSend(channelEvent)
      }

    case channelEvent: ChatEvent =>
      encodeAndSend(channelEvent)

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
      actor ! UserInfo(s"${user.name} is using ${encodeClient(user.client)}${if (user.channel != "") s" in the channel ${user.channel}" else ""}.")

    case BanCommand(kicking, message) =>
      self ! UserInfo(YOU_KICKED(kicking))
      publish(TOPIC_CHANNELS, UserSwitchedChat(self, user, THE_VOID))

    case KickCommand(kicking, message) =>
      self ! UserInfo(YOU_KICKED(kicking))
      publish(TOPIC_CHANNELS, UserSwitchedChat(self, user, THE_VOID))

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
      if (Config.AntiFlood.enabled && floodState(data.length)) {
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
                if (!user.channel.equalsIgnoreCase(channel)) {
                  publish(TOPIC_CHANNELS, UserSwitchedChat(self, fromUser, channel))
                }

/*                if (!user.channel.equalsIgnoreCase(channel)) {
                  implicit val timeout = Timeout(1000, TimeUnit.MILLISECONDS)
                  Await.result(channelsActor ? UserSwitchedChat(self, fromUser, channel), timeout.duration) match {
                    case command@UserChannel(newUser, channel, channelActor) =>
                      channelTopic = Some(TOPIC_CHANNEL(channel))
                      subscribe(channelTopic.get)
                      channelActor ! GetUsers
                      user = newUser
                      encodeAndSend(command)
                    case command: ChatEvent =>
                      encodeAndSend(command)
                    case _ =>
                  }
                }*/
              case ResignCommand => resign()
              case RejoinCommand => rejoin()
              case command: ChannelCommand =>
                channelTopic.foreach(publish(command))
//                if (channelActor != ActorRef.noSender) {
//                  channelActor ! command
//                }
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
                publish(TOPIC_DAO, CreateAccount(username, passwordHash))
              case ChangePasswordCommand(newPassword) =>
                publish(TOPIC_DAO, UpdateAccountPassword(user.name, newPassword))
              case SplitMe =>
                if (Flags.isAdmin(user)) {
                  //publish(TOPIC_SPLIT, SplitMe)
                  cluster.down(cluster.selfAddress)
                }
              case SendBirth =>
                if (Flags.isAdmin(user)) {
                  cluster.join(cluster.selfAddress)
                  //publish(TOPIC_ONLINE, ServerOnline)
                }
              case command @ BroadcastCommand(message) =>
                usersActor ! command
              case command @ DisconnectCommand(user) =>
                usersActor ! command
              case command @ CloseAccountCommand(account, reason) =>
                publish(TOPIC_DAO, CloseAccount(account, reason))
              case command @ OpenAccountCommand(account) =>
                publish(TOPIC_DAO, OpenAccount(account))
              case _ =>
            }
          case x =>
            log.debug("{} UserMessageDecoder Unhandled {}", user.name, x)
        }
      }

    case command: UserToChannelCommandAck =>
      //log.error(s"UTCCA $command")
      channelTopic.foreach(publish(command))
//      if (channelActor != ActorRef.noSender) {
//        channelActor ! command
//      }

    case Terminated(actor) =>
      publish(TOPIC_CHANNELS, RemUser(self))
      publish(TOPIC_USERS, Rem(self))
      self ! PoisonPill

    case KillConnection =>
      connection ! PoisonPill

    case x =>
      log.debug("{} UserActor Unhandled {}", user.name, x)
  }

  private def handlePingResponse(cookie: String) = {
    if (channelTopic.isDefined /*channelActor != ActorRef.noSender*/ && pingCookie == cookie) {
      val updatedPing = Math.max(0, System.currentTimeMillis() - pingTime).toInt
      if (updatedPing <= 60000) {
        channelTopic.foreach(publish(UpdatePing(updatedPing)))
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
    channelTopic.foreach(publish(RemUser(self)))
    channelTopic = None
//    channelActor ! RemUser(self)
//    channelActor = ActorRef.noSender
    user = user.copy(channel = "")
    self ! Received(ByteString(s"/j $oldChannel"))
  }
}
