package com.init6.channels

import akka.actor.ActorRef
import com.init6.Constants._
import com.init6.coders.commands.{BanCommand, KickCommand, OperableCommand, UnbanCommand}
import com.init6.users.UserToChannelCommandAck
import com.init6.utils.CaseInsensitiveFiniteHashSet

/**
  * Created by filip on 11/24/15.
  */
trait BannableChannelActor extends ChannelActor {

  // Banned users
  val bannedUsers = CaseInsensitiveFiniteHashSet(limit)

  override def receiveEvent = ({
    case command: UserToChannelCommandAck =>
      users.get(sender()).foreach(user => {
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
            case _: OperableCommand =>
              if (isLocal()) {
                sender() ! UserError(NOT_OPERATOR)
              }
            case _ => super.receiveEvent(command)
          }
        }
      })
  }: Receive)
    .orElse(super.receiveEvent)

  override def add(actor: ActorRef, user: User): User = {
    if (bannedUsers(user.name)) {
      if (isLocal()) {
        sender() ! UserError(YOU_BANNED)
      }
      user
    } else {
      super.add(actor, user)
    }
  }

  override def rem(actor: ActorRef): Option[User] = {
    val userOpt = super.rem(actor)

    if (users.isEmpty) {
      bannedUsers.clear()
    }

    userOpt
  }

  def kickAction(kickingActor: ActorRef, kickedActor: ActorRef, message: String) = {
    users.get(kickedActor).fold(
      kickingActor ! UserError(INVALID_USER)
    )(kickedUser => {
      if (Flags.canBan(kickedUser)) {
        if (isLocal(kickingActor)) {
          kickingActor ! UserError(CANNOT_KICK_OPERATOR)
        }
      } else {
        val kicking = users(kickingActor).name

        localUsers ! UserInfo(USER_KICKED(kicking, kickedUser.name, message))
        if (isLocal(kickedActor)) {
          kickedActor ! KickCommand(kicking)
        }
      }
    })
  }

  def banAction(banningActor: ActorRef, bannedActor: ActorRef, banned: String, message: String) = {
    println("banAction " + banningActor + " - " + bannedActor + " - " + banned + " - " + sender())
    val banning = users(banningActor).name

    users.get(bannedActor).fold({
      bannedUsers += banned
      localUsers ! UserInfo(USER_BANNED(banning, banned, message))
    })(bannedUser => {
      if (Flags.canBan(bannedUser)) {
        if (isLocal(banningActor)) {
          banningActor ! UserError(CANNOT_BAN_OPERATOR)
        }
      } else {
        bannedUsers += banned
        if (isLocal(bannedActor)) {
          bannedActor ! BanCommand(banning)
        }
        localUsers ! UserInfo(USER_BANNED(banning, banned, message))
      }
    })
  }

  def unbanAction(unbanningActor: ActorRef, unbanned: String) = {
    val unbanning = users(unbanningActor).name

    if (bannedUsers(unbanned)) {
      bannedUsers -= unbanned
      localUsers ! UserInfo(USER_UNBANNED(unbanning, unbanned))
    } else {
      if (isLocal(unbanningActor)) {
        unbanningActor ! UserError(NOT_BANNED)
      }
    }
  }

  override def whoCommand(actor: ActorRef, user: User) = {
    if (!bannedUsers(user.name)) {
      super.whoCommand(actor, user)
    } else {
      actor ! WhoCommandError(NOT_ALLOWED_TO_VIEW)
    }
  }
}
