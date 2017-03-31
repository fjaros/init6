package com.init6.channels

import akka.actor.ActorRef
import com.init6.Constants._
import com.init6.coders.commands._
import com.init6.users.UserToChannelCommandAck

import scala.collection.mutable

/**
  * Created by filip on 11/15/15.
  */
trait ChattableChannelActor extends ChannelActor {

  val mutedUsers = mutable.HashSet.empty[ActorRef]

  override def receiveEvent = ({
    case ChatCommand(user, message) =>
      onChatMessage(user, message)
    case EmoteCommand(user, message) =>
      onEmoteMessage(user, message)

    case command: UserToChannelCommandAck =>
      command.command match {
        case _: SquelchCommand =>
          if (isLocal()) {
            sender() ! UserSquelched(command.realUsername)
            sender() ! UserInfo(USER_SQUELCHED(command.realUsername))
            users.get(command.userActor).foreach(user => sender() ! UserFlags(Flags.squelch(user)))
          }

        case _: UnsquelchCommand =>
          if (isLocal()) {
            sender() ! UserUnsquelched(command.realUsername)
            sender() ! UserInfo(USER_UNSQUELCHED(command.realUsername))
            users.get(command.userActor).foreach(user => sender() ! UserFlags(Flags.unsquelch(user)))
          }

        case userMute: UserMute =>
          mutedUsers += command.userActor
          if (isLocal()) {
            sender() ! UserInfo(USER_MUTED(command.realUsername, name))
          }

        case userUnmute: UserUnmute =>
          mutedUsers -= command.userActor
          if (isLocal()) {
            sender() ! UserInfo(USER_UNMUTED(command.realUsername, name))
          }
        case _ =>
      }
      super.receiveEvent(command)

    case TopicCommand(_, message) =>
      val userActor = sender()
      users.get(userActor).foreach(user => {
        if (Flags.canBan(user)) {
          topicExchange = TopicExchange(message, System.currentTimeMillis())
          localUsers ! UserInfo(SET_TOPIC(user.name, topicExchange.topic))
        } else {
          if (isLocal()) {
            userActor ! UserError(NOT_OPERATOR)
          }
        }
      })

    case command: ChannelBroadcastable =>
      localUsers ! UserInfo(command.message)

  }: Receive)
    .orElse(super.receiveEvent)


  def onChatMessage(user: User, message: String) = {
    val userActor = sender()

    if (isLocal(userActor)) {
      userActor ! ITalked(user, message)
    }

    if (!mutedUsers.contains(userActor)) {
      localUsers
        .filterNot(_ == userActor)
        .foreach(_ ! UserTalked(user, message))
    }
  }

  def onEmoteMessage(user: User, message: String) = {
    val userActor = sender()

    if (mutedUsers.contains(userActor)) {
      if (isLocal()) {
        userActor ! UserEmote(user, message)
      }
    } else {
      localUsers ! UserEmote(user, message)
    }
  }

  override def add(actor: ActorRef, user: User): User = {
    val newUser = super.add(actor, user)

    localUsers
      .filterNot(_ == actor)
      .foreach(_ ! UserJoined(newUser))

    newUser
  }

  override def rem(actor: ActorRef): Option[User] = {
    val userOpt = super.rem(actor)
    userOpt.foreach(localUsers ! UserLeft(_))
    userOpt
  }
}
