package com.vilenet.channels

import akka.actor.ActorRef
import com.vilenet.Constants._
import com.vilenet.coders.commands._
import com.vilenet.users.UserToChannelCommandAck

/**
  * Created by filip on 11/15/15.
  */
trait ChattableChannelActor extends ChannelActor {

  override def receiveEvent = ({
    case ChatCommand(user, message) =>
      onChatMessage(user, message)
    case EmoteCommand(user, message) =>
      onEmoteMessage(user, message)

    case command: UserToChannelCommandAck =>
      command.command match {
        case _: SquelchCommand =>
          sender() ! UserInfo(USER_SQUELCHED(command.realUsername))
          sender() ! UserSquelched(command.realUsername)
          users.get(command.userActor).foreach(user => sender() ! UserFlags(Flags.squelch(user)))
        case _: UnsquelchCommand =>
          sender() ! UserInfo(USER_UNSQUELCHED(command.realUsername))
          sender() ! UserUnsquelched(command.realUsername)
          users.get(command.userActor).foreach(user => sender() ! UserFlags(Flags.unsquelch(user)))
        case _ =>
      }
      super.receiveEvent(command)

    case TopicCommand(_, message) =>
      val userActor = sender()
      users.get(userActor).foreach(user => {
        if (Flags.canBan(user)) {
          topic = message
          localUsers ! UserInfo(SET_TOPIC(user.name, topic))
        } else {
          if (isLocal()) {
            userActor ! UserError(NOT_OPERATOR)
          }
        }
      })
  }: Receive)
    .orElse(super.receiveEvent)


  def onChatMessage(user: User, message: String) = {
    val userActor = sender()

    if (isLocal(userActor)) {
      userActor ! ITalked(user, message)
    }
    localUsers
      .filterNot(_ == userActor)
      .foreach(_ ! UserTalked(user, message))
  }

  def onEmoteMessage(user: User, message: String) = {
    localUsers ! UserEmote(user, message)
  }

  override def add(actor: ActorRef, user: User): User = {
    val newUser = super.add(actor, user)

    localUsers
      .filterNot(_ == actor)
      .foreach(_ ! UserJoined(newUser))

    newUser
  }

  override def rem(actor: ActorRef): Option[User] = {
    if (name.equalsIgnoreCase("vile")) {
      println("##SPECIAL REM " + actor + " - " + sender())
    }
    val userOpt = super.rem(actor)
    userOpt.foreach(localUsers ! UserLeft(_))
    userOpt
  }
}
