package com.vilenet.channels

import akka.actor.ActorRef
import com.vilenet.Constants._
import com.vilenet.coders.commands.{OperableCommand, UnbanCommand, BanCommand, KickCommand}
import com.vilenet.users.UserToChannelCommandAck

/**
  * Created by filip on 11/24/15.
  */
trait BannableChannelActor extends RemoteBannableChannelActor {

  override def receiveEvent = ({
    case command: UserToChannelCommandAck =>
      users.get(sender()).fold()(user => {
        if (Flags.canBan(user)) {
          command.command match {
            case KickCommand(kicked, message) =>
              kickAction(sender(), command.userActor, message)
            case BanCommand(banned, message) =>
              banAction(sender(), command.userActor, command.realUsername, message)
            case UnbanCommand(unbanned) =>
              unbanAction(sender(), command.realUsername)
            case _ => super.receiveEvent(command)
          }
        } else {
          command.command match {
            case command: OperableCommand => sender() ! UserError(NOT_OPERATOR)
            case _ => super.receiveEvent(command)
          }
        }
      })
  }: Receive)
    .orElse(super.receiveEvent)

  override def add(actor: ActorRef, user: User): User = {
    if (bannedUsers(user.name)) {
      actor ! UserError(YOU_BANNED)
      user
    } else {
      super.add(actor, user)
    }
  }

  def kickAction(kickingActor: ActorRef, kickedActor: ActorRef, message: String) = {
    users.get(kickedActor).fold(
      kickingActor ! UserError(INVALID_USER)
    )(kickedUser => {
      if (Flags.canBan(kickedUser)) {
        kickingActor ! UserError(CANNOT_KICK_OPERATOR)
      } else {
        val kicking = users(kickingActor).name

        localUsers ! UserInfo(USER_KICKED(kicking, kickedUser.name, message))
        kickedActor ! KickCommand(kicking)
      }
    })
  }

  override def banAction(banningActor: ActorRef, bannedActor: ActorRef, banned: String, message: String) = {
    val banning = users(banningActor).name

    users.get(bannedActor).fold({
      bannedUsers += banned
      super.banAction(banningActor, bannedActor, banned, message)
      localUsers ! UserInfo(USER_BANNED(banning, banned, message))
    })(bannedUser => {
      if (Flags.canBan(bannedUser)) {
        banningActor ! UserError(CANNOT_BAN_OPERATOR)
      } else {
        bannedUsers += banned
        super.banAction(banningActor, bannedActor, banned, message)
        bannedActor ! BanCommand(banning)
        localUsers ! UserInfo(USER_BANNED(banning, banned, message))
      }
    })
  }

  override def unbanAction(unbanningActor: ActorRef, unbanned: String) = {
    val unbanning = users(unbanningActor).name

    if (bannedUsers(unbanned)) {
      bannedUsers -= unbanned
      super.unbanAction(unbanningActor, unbanned)
      localUsers ! UserInfo(USER_UNBANNED(unbanning, unbanned))
    } else {
      unbanningActor ! UserError(NOT_BANNED)
    }
  }

  override def whoCommand(actor: ActorRef, user: User) = {
    if (!bannedUsers(user.name)) {
      super.whoCommand(actor, user)
    } else {
      actor ! UserError(NOT_ALLOWED_TO_VIEW)
    }
  }
}
