package com.vilenet.users

import akka.actor.{Terminated, ActorRef, Props}
import akka.io.Tcp.{Received, Write}
import com.vilenet.ViLeNetActor
import com.vilenet.channels._
import com.vilenet.coders.Encoder
import com.vilenet.coders.binary.BinaryChatEncoder
import com.vilenet.coders.telnet._

/**
 * Created by filip on 9/27/15.
 */
object UserActor {
  def apply(connection: ActorRef, user: User, protocol: Protocol) = Props(new UserActor(connection, user,
    protocol match {
      case BinaryProtocol => BinaryChatEncoder
      case TelnetProtocol => TelnetEncoder
  }))
}

case object GetUser


class UserActor(connection: ActorRef, var user: User, encoder: Encoder) extends ViLeNetActor {

  var channelActor: ActorRef = _

  context.watch(connection)

  override def receive: Receive = {
    case channelEvent: ChatEvent =>
      encoder(channelEvent)
        .fold()(message => {
        connection ! Write(message)
        channelEvent match {
          case UserChannel(_, channel, channelActor) =>
            user = user.copy(channel = channel)
            this.channelActor = channelActor
          case _ =>
        }
      })
    case (actor: ActorRef, WhisperMessage(fromUser, toUsername, message)) =>
      encoder(UserWhisperedFrom(fromUser, message))
        .fold()(msg => {
          connection ! Write(msg)
          actor !  UserWhisperedTo(user, message)
        })
    case (actor: ActorRef, WhoisCommand(fromUser, username)) =>
      actor ! UserInfo(s"${user.name} is using ${user.client} in the channel ${user.channel}.")
    case (actor: ActorRef, DesignateCommand(fromUser, username)) =>
      //system.actorSelection(s"${channelsActor.pathString}/${fromUser.channel.toLowerCase}") ! DesignateUser(actor, self)
    case Received(data) =>
      UserMessageDecoder(user, data) match {
        case command: Command =>
          log.error(s"UserMessageDecoder $command")
          command match {
            /**
             * The channel command and user command have two different flows.
             *  A user has to go through a middle-man users actor because there is no guarantee the receiving user is online.
             *  A command being sent to the user's channel can be done via actor selection, since we can guarantee the
             *  channel exists.
             */
            case JoinUserCommand(fromUser, channel) => channelsActor ! UserSwitchedChat(self, fromUser, channel)
            case command: ChannelCommand => channelActor ! command
            case command: UserCommand => usersActor ! command
            case command: ReturnableCommand => encoder(command).fold()(connection ! Write(_))
            case _ =>
          }
        case _ =>
      }
    case command: UserToChannelCommandAck =>
      log.error(s"UTCCA $command")
      //channelActor ! command
    case Terminated(actor) =>
      context.stop(self)
    case x =>
      //log.error(s"Received weird $x")
  }
}
