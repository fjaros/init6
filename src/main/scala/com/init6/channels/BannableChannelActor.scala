package com.init6.channels

import akka.actor.{ActorRef, Address}
import com.init6.Constants._
import com.init6.channels.utils.BannedMap
import com.init6.coders.commands.{BanCommand, KickCommand, UnbanCommand}
import com.init6.users.UserToChannelCommandAck

/**
  * Created by filip on 11/24/15.
  */
trait BannableChannelActor extends ChannelActor {

  // Banned users
  val bannedUsers = BannedMap(limit)

  // Unbans existing names if server drops
  // (probably shouldn't do that)
  override protected def onServerDead(address: Address) = {
    remoteUsersMap
      .get(address)
      .foreach(actors => {
        actors
          .map(users)
          .foreach(bannedUsers -= _.name)
      })

    super.onServerDead(address)
  }

  override def receiveEvent = ({
    case command @ GetChannelUsers =>
      sender() ! ReceivedBannedUsers(bannedUsers.toImmutable)
      super.receiveEvent(command)

    case ReceivedBannedUsers(names) =>
      bannedUsers ++= names

    case command: UserToChannelCommandAck =>
      users.get(sender()).foreach(user => {
//        if (Flags.canBan(user)) {
          command.command match {
            case KickCommand(kicked, message) =>
              kickAction(sender(), command.userActor, message)
            case BanCommand(banned, message) =>
              banAction(sender(), command.userActor, command.realUsername, message)
            case UnbanCommand(unbanned) =>
              unbanAction(sender(), command.realUsername)
            case _ => super.receiveEvent(command)
          }
//        } else {
//          command.command match {
//            case _: OperableCommand =>
//              if (isLocal()) {
//                sender() ! UserError(NOT_OPERATOR)
//              }
//            case _ => super.receiveEvent(command)
//          }
//        }
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

  // remove users from banned when server reconnects if they are already in the channel
  // the ops on other servers will have to ban them again.
  override def remoteIn(remoteUserActor: ActorRef, user: User) = {
    bannedUsers -= user.name

    super.remoteIn(remoteUserActor, user)
  }

  override def rem(actor: ActorRef): Option[User] = {
    val userOpt = super.rem(actor)

    bannedUsers -= actor

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
    log.info("banAction " + banningActor + " - " + bannedActor + " - " + banned + " - " + sender())
    val banning = users(banningActor).name

    users.get(bannedActor).fold({
      bannedUsers += banningActor -> banned
      localUsers ! UserInfo(USER_BANNED(banning, banned, message))
    })(bannedUser => {
      if (Flags.canBan(bannedUser)) {
        if (isLocal(banningActor)) {
          banningActor ! UserError(CANNOT_BAN_OPERATOR)
        }
      } else {
        bannedUsers += banningActor -> banned
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
