package com.init6.coders.commands

import com.init6.Constants._
import com.init6.channels.UserError

/**
  * Created by filip on 2/9/17.
  */
object FriendsCommand {

  def apply(message: String): Command = {
    val (command, rest) = CommandDecoder.spanBySpace(message)
    command.toLowerCase match {
      case "add" | "a" => emptyOrDoArgument(rest, FRIENDS_ADD_NO_USERNAME, FriendsAdd(rest))
      case "demote" | "d" => emptyOrDoArgument(rest, FRIENDS_DEMOTE_NO_USERNAME, FriendsDemote(rest))
      case "list" | "l" => FriendsList()
      case "msg" | "m" => emptyOrDoArgument(rest, NO_MESSAGE_INPUT, FriendsMsg(rest))
      case "promote" | "p" => emptyOrDoArgument(rest, FRIENDS_PROMOTE_NO_USERNAME, FriendsPromote(rest))
      case "remove" | "r" => emptyOrDoArgument(rest, FRIENDS_REMOVE_NO_USERNAME, FriendsRemove(rest))
    }
  }

  def emptyOrDoArgument(rest: String, emptyConstant: String, command: Command) = {
    if (rest.nonEmpty) {
      command
    } else {
      UserError(emptyConstant)
    }
  }
}

trait FriendsCommand extends Command

case class FriendsAdd(who: String) extends FriendsCommand
case class FriendsDemote(who: String) extends FriendsCommand
case class FriendsList() extends FriendsCommand
case class FriendsMsg(msg: String) extends FriendsCommand
case class FriendsPromote(who: String) extends FriendsCommand
case class FriendsRemove(who: String) extends FriendsCommand
case class FriendsWhois(position: Int, who: String) extends FriendsCommand
case class FriendsWhoisResponse(online: Boolean, position: Int, username: String, client: String, channel: String, server: String) extends FriendsCommand
