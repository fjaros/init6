package com.vilenet.channels

import akka.actor.ActorRef
import com.vilenet.Constants._
import com.vilenet.coders.{UnbanCommand, BanCommand, KickCommand}
import com.vilenet.users.UserToChannelCommandAck
import com.vilenet.utils.CaseInsensitiveFiniteHashSet

/**
  * Created by filip on 11/24/15.
  */
trait BannableChannelActor extends RemoteBannableChannelActor {

  override def receiveEvent = ({
    case command: UserToChannelCommandAck =>
      operatorAction(sender(),
        command.command match {
          case KickCommand(kicked) =>
            kickAction(sender(), command.userActor)
          case BanCommand(banned) =>
            banAction(sender(), command.userActor, command.realUsername)
          case UnbanCommand(unbanned) =>
            unbanAction(sender(), unbanned)
          case _ => super.receiveEvent(command)
        }
      )
  }: Receive).orElse(super.receiveEvent)

  override def add(actor: ActorRef, user: User): User = {
    if (bannedUsers(user.name)) {
      actor ! UserError(YOU_BANNED)
      user
    } else {
      super.add(actor, user)
    }
  }

  def kickAction(kickingActor: ActorRef, kickedActor: ActorRef) = {
    users.get(kickedActor).fold(
      kickingActor ! UserError(INVALID_USER)
    )(kickedUser => {
      val kicking = users(kickingActor).name

      localUsers ! UserInfo(USER_KICKED(kicking, kickedUser.name))
      kickedActor ! KickCommand(kicking)
    })
  }

  def operatorAction(actor: ActorRef, action: => Any) = {
    users.get(actor).fold({
      log.error("")
    })(user => {
      if (Flags.canBan(user)) {
        action
      } else {
        actor ! UserError(NOT_OPERATOR)
      }
    })
  }

  override def banAction(banningActor: ActorRef, bannedActor: ActorRef, banned: String) = {
    val banning = users(banningActor).name

    users.get(bannedActor).fold({
      bannedUsers += banned
      super.banAction(banningActor, bannedActor, banned)
      localUsers ! UserInfo(USER_BANNED(banning, banned))
    })(bannedUser => {
      if (Flags.canBan(bannedUser)) {
        banningActor ! UserError(CANNOT_BAN_OPERATOR)
      } else {
        bannedUsers += banned
        super.banAction(banningActor, bannedActor, banned)
        bannedActor ! BanCommand(banning)
        localUsers ! UserInfo(USER_BANNED(banning, banned))
      }
    })
  }

  override def unbanAction(unbanningActor: ActorRef, unbanned: String) = {
    val unbanning = users(unbanningActor).name

    if (bannedUsers(unbanned)) {
      bannedUsers -= unbanned
      super.unbanAction(unbanningActor, unbanned)
      localUsers ! UserInfo(USER_UNBANNED(unbanned, unbanning))
    } else {
      unbanningActor ! UserError(NOT_BANNED)
    }
  }
}
