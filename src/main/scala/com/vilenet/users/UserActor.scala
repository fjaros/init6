package com.vilenet.users

import akka.actor.{Terminated, ActorRef, Props}
import akka.io.Tcp.{Received, Write}
import com.vilenet.ViLeNetActor
import com.vilenet.channels._
import com.vilenet.coders.telnet._

/**
 * Created by filip on 9/27/15.
 */
object UserActor {
  def apply(connection: ActorRef, user: User) = Props(new UserActor(connection, user))
}

case object GetUser

class UserActor(connection: ActorRef, var user: User) extends ViLeNetActor {

  context.watch(connection)

  override def receive: Receive = {
    case channelEvent: ChatEvent =>
      TelnetEncoder(channelEvent)
        .fold()(message => {
        connection ! Write(message)
        channelEvent match {
          case UserChannel(_, channel) => user = user.copy(channel = channel)
          case _ =>
        }
      })
    case (actor: ActorRef, WhisperMessage(fromUser, toUsername, message)) =>
      TelnetEncoder(UserWhisperedFrom(fromUser, message))
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
            case command: ChannelCommand => channelsActor ! command
            case command: UserCommand => usersActor ! command
            case command: ReturnableCommand => TelnetEncoder(command).fold()(connection ! Write(_))
            case _ =>
          }
        case _ =>
      }
    case Terminated(actor) =>
      context.stop(self)
    case x =>
      //log.error(s"Received weird $x")
  }
}
