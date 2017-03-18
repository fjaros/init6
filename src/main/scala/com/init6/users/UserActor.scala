package com.init6.users

import java.util.concurrent.TimeUnit

import akka.actor.{ActorRef, PoisonPill, Props, Terminated}
import akka.io.Tcp.{Abort, Received, ResumeAccepting}
import akka.pattern.ask
import akka.util.Timeout
import com.init6.Constants._
import com.init6.channels._
import com.init6.channels.utils.ChannelJoinValidator
import com.init6.coders._
import com.init6.coders.binary.BinaryChatEncoder
import com.init6.coders.chat1.Chat1Encoder
import com.init6.coders.commands._
import com.init6.coders.telnet._
import com.init6.connection.{ConnectionInfo, IpBan, WriteOut}
import com.init6.db._
import com.init6.servers.{SendBirth, SplitMe}
import com.init6.utils.FutureCollector.futureSeqToFutureCollector
import com.init6.utils.{CaseInsensitiveHashSet, ChatValidator}
import com.init6.{Config, Init6Actor, Init6LoggingActor, ReloadConfig}

import scala.concurrent.Await

/**
 * Created by filip on 9/27/15.
 */
object UserActor {
  def apply(connectionInfo: ConnectionInfo, user: User, protocol: Protocol) =
    Props(classOf[UserActor], connectionInfo, user,
      protocol match {
        case BinaryProtocol => BinaryChatEncoder
        case TelnetProtocol => TelnetEncoder
        case Chat1Protocol => Chat1Encoder
      }
    )
}

case class JoinChannelFromConnection(channel: String, forceJoin: Boolean)
case class UserUpdated(user: User) extends ChatEvent
case class PingSent(time: Long, cookie: String) extends Command
case class UpdatePing(ping: Int) extends Command
case object KillConnection extends Command
case class DisconnectOnIp(ipAddress: Array[Byte]) extends Command

class UserActor(connectionInfo: ConnectionInfo, var user: User, encoder: Encoder)
  extends FloodPreventer with Init6Actor with Init6LoggingActor {

  import system.dispatcher

  var isTerminated = false
  var channelActor = ActorRef.noSender
  var squelchedUsers = CaseInsensitiveHashSet()
  var friendsList: Option[Seq[DbFriend]] = None
  val awayAvailablity = AwayAvailablity(user.name)
  val dndAvailablity = DndAvailablity(user.name)

  var pingTime: Long = 0
  var pingCookie: String = ""
  val connectedTime = System.currentTimeMillis()

  override def preStart() = {
    super.preStart()

    context.watch(connectionInfo.actor)
  }

  def checkSquelched(user: User) = {
    if (squelchedUsers.contains(user.name)) {
      Flags.squelch(user)
    } else {
      Flags.unsquelch(user)
    }
  }

  def encodeAndSend(chatEvent: ChatEvent) = {
    encoder(chatEvent).foreach(message => connectionInfo.actor ! WriteOut(message))
  }

  override def loggedReceive: Receive = {
    case JoinChannelFromConnection(channel, forceJoin) =>
      joinChannel(channel, forceJoin)
      connectionInfo.actor ! ResumeAccepting(1)

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

    case ChannelJoinResponse(event) =>
      event match {
        case UserChannel(newUser, channel, flags, channelActor, channelSize) =>
          user = newUser
          this.channelActor = channelActor
          channelActor ! GetUsers
        case _ =>
      }
      encodeAndSend(event)

    case UserSquelched(username) =>
      squelchedUsers += username

    case UserUnsquelched(username) =>
      squelchedUsers -= username

    case chatEvent: ChatEvent =>
      handleChatEvent(chatEvent)

    case (actor: ActorRef, WhisperMessage(fromUser, toUsername, message, sendNotification)) =>
      encoder(UserWhisperedFrom(fromUser, message))
        .foreach(msg => {
          dndAvailablity
            .whisperAction(actor)
            .getOrElse({
              connectionInfo.actor ! WriteOut(msg)
              awayAvailablity.whisperAction(actor)
              if (sendNotification) {
                actor ! UserWhisperedTo(user, message)
              }
            })
        })

    case (actor: ActorRef, WhoisCommand(fromUser, username)) =>
      actor !
        (if (Flags.isAdmin(fromUser)) {
          UserInfo(s"${user.name} (${user.ipAddress}) is using ${encodeClient(user.client)}${if (user.inChannel != "") s" in the channel ${user.inChannel}" else ""} on server ${Config().Server.host}.")
        } else {
          UserInfo(s"${user.name} is using ${encodeClient(user.client)}${if (user.inChannel != "") s" in the channel ${user.inChannel}" else ""} on server ${Config().Server.host}.")
        })

    case (actor: ActorRef, FriendsWhois(position, username)) =>
      actor ! FriendsWhoisResponse(online = true, position, user.name, user.client, user.inChannel, Config().Server.host)

    case (actor: ActorRef, PlaceOfUserCommand(_, _)) =>
      actor ! UserInfo(USER_PLACED(user.name, connectionInfo.place, Config().Server.host))

    case WhoCommandResponse(whoResponseMessage, userMessages) =>
      whoResponseMessage.fold(encodeAndSend(UserErrorArray(CHANNEL_NOT_EXIST)))(whoResponseMessage => {
        encodeAndSend(UserInfo(whoResponseMessage))
        userMessages.foreach(userMessage => encodeAndSend(UserInfo(userMessage)))
      })

    case WhoCommandError(errorMessage) =>
      encodeAndSend(UserError(errorMessage))

    case ShowBansResponse(chatEvent: ChatEvent) =>
      encodeAndSend(chatEvent)

    case PrintChannelUsersResponse(chatEvent: ChatEvent) =>
      encodeAndSend(chatEvent)

    case BanCommand(kicking, message) =>
      self ! UserInfo(YOU_KICKED(kicking))
      joinChannel(THE_VOID)

    case KickCommand(kicking, message) =>
      self ! UserInfo(YOU_KICKED(kicking))
      joinChannel(THE_VOID)

    case DAOCreatedAck(username, passwordHash) =>
      self ! UserInfo(ACCOUNT_CREATED(username, passwordHash))

    case DAOUpdatedPasswordAck(username, passwordHash) =>
      self ! UserInfo(ACCOUNT_UPDATED(username, passwordHash))

    case DAOClosedAccountAck(username, reason) =>
      self ! UserInfo(ACCOUNT_CLOSED(username, reason))

    case DAOOpenedAccountAck(username) =>
      self ! UserInfo(ACCOUNT_OPENED(username))

    case DAOAliasCommandAck(aliasTo) =>
      self ! UserInfo(ACCOUNT_ALIASED(aliasTo))

    case DAOAliasToCommandAck(aliasTo) =>
      self ! UserInfo(ACCOUNT_ALIASED_TO(aliasTo))

    case DAOFriendsAddResponse(friendsList, friend) =>
      this.friendsList = Some(friendsList)
      self ! UserInfo(FRIENDS_ADDED_FRIEND(friend.friend_name))

    case DAOFriendsListToListResponse(friendsList) =>
      this.friendsList = Some(friendsList)
      sendFriendsList(friendsList)

    case DAOFriendsListToMsgResponse(friendsList, msg) =>
      this.friendsList = Some(friendsList)
      sendFriendsMsg(friendsList, msg)

    case DAOFriendsRemoveResponse(friendsList, friend) =>
      this.friendsList = Some(friendsList)
      self ! UserInfo(FRIENDS_REMOVED_FRIEND(friend.friend_name))

    case ReloadDbAck =>
      self ! UserInfo(s"$INIT6_SPACE database reloaded.")

    case command: FriendsCommand =>
      handleFriendsCommand(command)

    // THIS SHIT NEEDS TO BE REFACTORED!
    case Received(data) =>
      // sanity check
      if (!ChatValidator(data)) {
        connectionInfo.actor ! Abort
        self ! KillConnection
        return receive
      }

      val command = CommandDecoder(user, data)
      if (Config().AntiFlood.enabled && floodState(command, data.length)) {
        // Handle AntiFlood
        encodeAndSend(UserFlooded)
        ipLimiterActor ! IpBan(
          connectionInfo.ipAddress.getAddress.getAddress,
          System.currentTimeMillis + (Config().AntiFlood.ipBanTime * 1000)
        )
        self ! KillConnection
        return receive
      }

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
        case c @ JoinUserCommand(fromUser, channel) =>
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
        case command: ShowChannelBans => channelsActor ! command
        case command: WhoCommand => channelsActor ! command
        case command: PrintChannelUsers => channelsActor ! command
        case command: OperableCommand =>
          if (Flags.canBan(user)) {
            usersActor ! command
          } else {
            encoder(UserError(NOT_OPERATOR)).foreach(connectionInfo.actor ! WriteOut(_))
          }
        case command: UserToChannelCommand => usersActor ! command
        case command: UserCommand => usersActor ! command
        case command: ReturnableCommand => encoder(command).foreach(connectionInfo.actor ! WriteOut(_))
        case command@UsersCommand => usersActor ! command
        case command @ UptimeCommand => usersActor ! command
        case command: TopCommand =>
          if (command.serverIp != Config().Server.host) {
            system.actorSelection(remoteAddress(command.serverIp, INIT6_TOP_COMMAND_ACTOR)) ! command
          } else {
            topCommandActor ! command
          }
        case PlaceOfSelfCommand => encodeAndSend(UserInfo(PLACED(connectionInfo.place, Config().Server.host)))
        case command @ PlaceOnServerCommand(serverIp) =>
          if (command.serverIp != Config().Server.host) {
            system.actorSelection(remoteAddress(command.serverIp, INIT6_USERS_PATH)) ! command
          } else {
            encodeAndSend(UserInfo(SERVER_PLACE(getPlace, Config().Server.host)))
          }
        case AwayCommand(message) => awayAvailablity.enableAction(message)
        case DndCommand(message) => dndAvailablity.enableAction(message)
        case AccountMade(username, passwordHash) =>
          daoActor ! CreateAccount(username, passwordHash)
        case ChangePasswordCommand(newPassword) =>
          daoActor ! UpdateAccountPassword(user.name, newPassword)
        case AliasCommand(alias) =>
          daoActor ! DAOAliasCommand(user, alias)
        case AliasToCommand(alias) =>
          daoActor ! DAOAliasToCommand(user, alias)

        case command: FriendsCommand =>
          handleFriendsCommand(command)

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
        case command @ IpBanCommand(ipAddress, until) =>
          ipLimiterActor ! IpBan(ipAddress, until)
          usersActor ! command
        case command @ UnIpBanCommand(ipAddress) =>
          ipLimiterActor ! command
        case command @ CloseAccountCommand(account, reason) =>
          daoActor ! CloseAccount(account, reason)
        case command @ OpenAccountCommand(account) =>
          daoActor ! OpenAccount(account)
        case ReloadConfig =>
          Config.reload()
          self ! UserInfo(s"$INIT6_SPACE configuration reloaded.")
        case ReloadDb =>
          daoActor ! ReloadDb

        case PrintConnectionLimit =>
          ipLimiterActor ! PrintConnectionLimit
        case PrintLoginLimit =>
          usersActor ! PrintLoginLimit
        case StartRP =>
          channelsActor ! StartRP
        case EndRP =>
          channelsActor ! EndRP
        case _ =>
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
      usersActor ! Rem(connectionInfo.ipAddress, self)
      self ! PoisonPill

    case KillConnection =>
      //println("#KILLCONNECTION FROM " + sender() + " - FOR: " + self + " - " + user)
      connectionInfo.actor ! PoisonPill

    case DisconnectOnIp(ipAddress) =>
      if (!Flags.isAdmin(user) && this.connectionInfo.ipAddress.getAddress.getAddress.sameElements(ipAddress)) {
        connectionInfo.actor ! PoisonPill
      }

    case x =>
      log.debug("{} UserActor Unhandled {}", user.name, x)
  }

  private def handleChatEvent(chatEvent: ChatEvent) = {
    val chatEventSender = sender()
    // If it comes from Channels/*, make sure it is the current channelActor
    if (chatEventSender.path.parent.name != INIT6_CHANNELS_PATH || chatEventSender == channelActor) {
      chatEvent match {
        case UserUpdated(newUser) =>
          user = newUser

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

  private def handleFriendsCommand(friendsCommand: FriendsCommand): Unit = {
    if (user.id == 0) {
      encodeAndSend(UserError("Due to a pending fix, new accounts cannot use the friends list until the next server restart."))
      return
    }

    friendsCommand match {
      case FriendsAdd(who) =>
        if (user.name.equalsIgnoreCase(who)) {
          encodeAndSend(UserError(FRIENDS_ADD_NO_YOURSELF))
        } else {
          daoActor ! DAOFriendsAdd(user.id, who)
        }
      case FriendsDemote(who) =>
        encodeAndSend(UserError("/friends demote is not yet implemented."))
      case FriendsList() =>
        friendsList.fold(daoActor ! DAOFriendsListToList(user.id))(sendFriendsList)
      case FriendsMsg(msg) =>
        friendsList.fold(daoActor ! DAOFriendsListToMsg(user.id, msg))(friendsList => sendFriendsMsg(friendsList, msg))
      case FriendsPromote(who) =>
        encodeAndSend(UserError("/friends promote is not yet implemented."))
      case FriendsRemove(who) =>
        daoActor ! DAOFriendsRemove(user.id, who)
    }
  }

  private def sendFriendsList(friendsList: Seq[DbFriend]) = {
    if (friendsList.nonEmpty) {
      implicit val timeout = Timeout(1000, TimeUnit.MILLISECONDS)
      friendsList
        .map(friend => usersActor ? FriendsWhois(friend.friend_position, friend.friend_name)).collectResults {
        case c: FriendsWhoisResponse => Some(c)
      }
        .foreach(friendResponses => {
          val sortedFriends = friendResponses.sortBy(_.position)

          val replies = FRIENDS_HEADER +: sortedFriends.map(response => {
            if (response.online) {
              FRIENDS_FRIEND_ONLINE(response.position, response.username, encodeClient(response.client), response.channel, response.server)
            } else {
              FRIENDS_FRIEND_OFFLINE(response.position, response.username)
            }
          })
            .toArray

          encodeAndSend(UserInfoArray(replies))
        })
    } else {
      encodeAndSend(UserError(FRIENDS_LIST_NO_FRIENDS))
    }
  }

  private def sendFriendsMsg(friendsList: Seq[DbFriend], msg: String) = {
    usersActor ! WhisperToFriendsMessage(user, friendsList.map(_.friend_name), msg)
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

  private def joinChannel(channel: String, forceJoin: Boolean = false) = {
    if (Flags.isAdmin(user) ||
      !Config().Server.Chat.enabled ||
      Config().Server.Chat.channels.contains(channel.toLowerCase)
    ) {
      implicit val timeout = Timeout(2, TimeUnit.SECONDS)
      //println(user.name + " - " + self + " - SENDING JOIN")
      Await.result(channelsActor ? UserSwitchedChat(self, user, channel), timeout.duration) match {
        case ChannelJoinResponse(event) =>
          //println(user.name + " - " + self + " - RECEIVED JOIN")
          event match {
            case UserChannel(newUser, channel, flags, channelActor, channelSize) =>
              user = newUser
              this.channelActor = channelActor
              channelActor ! GetUsers

              topCommandActor ! UserChannelJoined(connectionInfo, user, channelSize)
            case _ =>
              // Seems best for most poopylicious bots that enjoy getting stuck in limbo
              // Basically throw to void on force join of channel is full/or is banned
              if (forceJoin && !user.inChannel.equalsIgnoreCase(THE_VOID)) {
                self ! JoinChannelFromConnection(THE_VOID, forceJoin)
              }
          }
          encodeAndSend(event)
      }
    } else {
      encodeAndSend(UserError(CHANNEL_RESTRICTED))
    }
  }
}
