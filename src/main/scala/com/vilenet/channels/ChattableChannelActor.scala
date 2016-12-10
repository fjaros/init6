package com.vilenet.channels

import akka.actor.ActorRef
import com.vilenet.Constants._
import com.vilenet.coders.commands.{UnsquelchCommand, SquelchCommand, EmoteCommand, ChatCommand}
import com.vilenet.users.UserToChannelCommandAck

/**
  * Created by filip on 11/15/15.
  */
trait ChattableChannelActor extends RemoteChattableChannelActor {

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
          users.get(command.userActor).fold()(user => sender() ! UserFlags(Flags.squelch(user)))
        case _: UnsquelchCommand =>
          sender() ! UserInfo(USER_UNSQUELCHED(command.realUsername))
          sender() ! UserUnsquelched(command.realUsername)
          users.get(command.userActor).fold()(user => sender() ! UserFlags(Flags.unsquelch(user)))
        case _ =>
      }
      super.receiveEvent(command)
  }: Receive)
    .orElse(super.receiveEvent)


  override def onChatMessage(user: User, message: String) = {
    localUsers
      .filterNot(_ == sender())
      .foreach(_ ! UserTalked(user, message))
    super.onChatMessage(user, message)
  }

  override def onEmoteMessage(user: User, message: String) = {
    localUsers ! UserEmote(user, message)
    super.onEmoteMessage(user, message)
  }

  override def add(actor: ActorRef, user: User): User = {
    println("Chattable add " + user)

    val userJoined = UserJoined(user)
    localUsers
      .foreach(_ ! userJoined)

    val newUser = super.add(actor, user)

    localUsers += actor

    users
      .values
      .foreach(actor ! UserIn(_))

    newUser
  }

  override def rem(actor: ActorRef): Option[User] = {
    val userOpt = super.rem(actor)
    userOpt.fold()(user => {
      localUsers -= actor
      localUsers ! UserLeft(user)
    })
    userOpt
  }

  override def remoteAdd(actor: ActorRef, user: User): Unit = {
    super.remoteAdd(actor, user)

    remoteUsers.get(sender()).fold(/*log.error(s"Remote user added but no remote channel actor found ${sender()}"*/)(_ += actor)

    val userJoined = UserJoined(user)
    localUsers
      .foreach(_ ! userJoined)
  }

  override def remoteRem(actor: ActorRef): Option[User] = {
    val userOpt = super.remoteRem(actor)

    //println(s"remoteRem Users $users")

    userOpt.fold()(user => {
      remoteUsers.get(sender()).fold(/*log.error(s"Remote user removed but no remote channel actor found ${sender()}"*/)(_ -= actor)
      val userLeft = UserLeft(user)
      localUsers
        .foreach(_ ! userLeft)
    })

    userOpt
  }
}

