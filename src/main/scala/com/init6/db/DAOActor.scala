package com.init6.db

import akka.actor.Props
import com.init6.Constants._
import com.init6.channels.{User, UserError}
import com.init6.coders.commands.Command
import com.init6.servers.Remotable
import com.init6.{Init6Component, Init6RemotingActor}

/**
  * Created by filip on 12/21/15.
  */
object DAOActor extends Init6Component {
  def apply() = system.actorOf(Props[DAOActor], INIT6_DAO_PATH)
}

case object ReloadDb extends Command with Remotable
case object ReloadDbAck extends Command
case class CreateAccount(username: String, passwordHash: Array[Byte]) extends Remotable
case class UpdateAccountPassword(username: String, passwordHash: Array[Byte]) extends Remotable
case class CloseAccount(username: String, reason: String = "") extends Remotable
case class OpenAccount(username: String) extends Remotable
case class DAOCreatedAck(username: String, passwordHash: Array[Byte]) extends Command
case class DAOUpdatedPasswordAck(username: String, passwordHash: Array[Byte]) extends Command
case class DAOClosedAccountAck(username: String, reason: String) extends Command
case class DAOOpenedAccountAck(username: String) extends Command

case class DAOAliasCommand(aliasToUser: User, aliasFrom: String) extends Command
case class DAOAliasCommandAck(aliasTo: String) extends Command
case class DAOAliasToCommand(aliasFromUser: User, aliasTo: String) extends Command
case class DAOAliasToCommandAck(aliasFrom: String) extends Command

case class DAOFriendsAdd(userId: Long, who: String) extends Command
case class DAOFriendsAddResponse(friends: Seq[DbFriend], addedFriend: DbFriend) extends Command
case class DAOFriendsListToList(userId: Long) extends Command
case class DAOFriendsListToListResponse(friends: Seq[DbFriend]) extends Command
case class DAOFriendsListToMsg(userId: Long, msg: String) extends Command
case class DAOFriendsListToMsgResponse(friends: Seq[DbFriend], msg: String) extends Command
case class DAOFriendsRemove(userId: Long, who: String) extends Command
case class DAOFriendsRemoveResponse(friends: Seq[DbFriend], removedFriend: DbFriend) extends Command
case class DAOUpdateLoggedInTime(username: String) extends Command

class DAOActor extends Init6RemotingActor {

  override val actorPath = INIT6_DAO_PATH

  override def receive: Receive = {
    case ReloadDb =>
      DAO.reloadCache()
      sender() ! ReloadDbAck

    case CreateAccount(username, passwordHash) =>
      DAO.createUser(username, passwordHash)
      if (isLocal()) {
        sender() ! DAOCreatedAck(username, passwordHash)
      }

    case UpdateAccountPassword(username, passwordHash) =>
      DAO.updateUser(username, password_hash = Some(passwordHash))
      if (isLocal()) {
        sender() ! DAOUpdatedPasswordAck(username, passwordHash)
      }

    case CloseAccount(username, reason) =>
      DAO.updateUser(username, closed = Some(true), closed_reason = Some(reason))
      if (isLocal()) {
        sender() ! DAOClosedAccountAck(username, reason)
      }

    case OpenAccount(username) =>
      DAO.updateUser(username, closed = Some(false), closed_reason = Some(""))
      if (isLocal()) {
        sender() ! DAOOpenedAccountAck(username)
      }

    case channelJoin: DbChannelJoin =>
      DAO.saveChannelJoin(channelJoin)

    case DAOAliasCommand(aliasToUser, aliasFrom) =>
      DAO.getUser(aliasFrom)
        .foreach(aliasFromUser => {
          DAO.updateUser(
            username = aliasFromUser.username,
            alias_id = Some(aliasToUser.id)
          )
          sender() ! DAOAliasCommandAck(aliasFrom)
        })

    case DAOAliasToCommand(aliasFromUser, aliasTo) =>
      DAO.getUser(aliasTo)
        .foreach(aliasToUser => {
          DAO.updateUser(
            username = aliasFromUser.name,
            alias_id = Some(aliasToUser.id)
          )
          sender() ! DAOAliasToCommandAck(aliasTo)
        })

    case DAOFriendsAdd(userId, who) =>
      DAO
        .getUser(who)
        .fold(sender() ! UserError(INVALID_USER))(dbUser => {
          DAO.friendsCache.get(userId).fold({
            val dbFriend = DAO.friendsCache.insert(userId, 1, dbUser.id, who)
            sender() ! DAOFriendsAddResponse(DAO.friendsCache.get(userId).get, dbFriend)
          })(dbFriends =>
            if (dbFriends.length >= 75) {
              sender() ! UserError(FRIENDS_ADD_MAXIMUM_REACHED)
            } else {
              if (dbFriends.map(_.friend_name.toLowerCase).contains(who.toLowerCase)) {
                sender() ! UserError(FRIENDS_ALREADY_FRIEND(who))
              } else {
                val dbFriend = DAO.friendsCache.insert(userId, dbFriends.length + 1, dbUser.id, who)
                sender() ! DAOFriendsAddResponse(DAO.friendsCache.get(userId).get, dbFriend)
              }
            }
          )
        })

    case DAOFriendsListToList(userId) =>
      sender() ! DAOFriendsListToListResponse(DAO.friendsCache.get(userId).getOrElse(Seq.empty))

    case DAOFriendsListToMsg(userId, msg) =>
      sender() ! DAOFriendsListToMsgResponse(DAO.friendsCache.get(userId).getOrElse(Seq.empty), msg)

    case DAOFriendsRemove(userId, who) =>
      DAO
        .getUser(who)
        .fold(sender() ! UserError(INVALID_USER))(dbUser => {
          DAO.friendsCache.get(userId).fold({
            sender() ! UserError(FRIENDS_REMOVE_NOT_ADDED(who))
          })(dbFriends => {
            val friendToRemove = dbFriends.find(_.friend_name.equalsIgnoreCase(who))

            if (friendToRemove.isEmpty) {
              sender() ! UserError(FRIENDS_REMOVE_NOT_ADDED(who))
            } else {
              val dbFriend = DAO.friendsCache.delete(userId, friendToRemove.get.friend_position)
              sender() ! DAOFriendsRemoveResponse(DAO.friendsCache.get(userId).get, dbFriend)
            }
          })
        })

    case DAOUpdateLoggedInTime(username) =>
      DAO.updateUser(username, last_logged_in = Some(System.currentTimeMillis))
  }
}
