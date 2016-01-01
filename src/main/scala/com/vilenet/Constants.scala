package com.vilenet

import scala.annotation.switch

/**
 * Created by filip on 10/2/15.
 */
object Constants {

  val CHARSET = "windows-1252"

  val TOPIC_DAO = "dao"
  val TOPIC_ONLINE = "online"
  val TOPIC_CHANNEL = "channel"
  val TOPIC_CHANNELS = "channels"
  val TOPIC_USERS = "users"
  val TOPIC_SPLIT = "split"

  val VILE_NET = "ViLeNet"
  val VILE_NET_DAO_PATH = "DAO"
  val VILE_NET_SERVERS_PATH = "Servers"
  val VILE_NET_CHANNELS_PATH = "Channels"
  val VILE_NET_USERS_PATH = "Users"
  val CHANNEL_DISPATCHER = "channel-dispatcher"


  val ALREADY_OPERATOR = "That user is already an operator."
  val AWAY_ENGAGED = "You are now marked as being away."
  val AWAY_CANCELLED = "You are no longer marked as away."
  val AWAY_UNAVAILABLE = (name: String, message: String) => s"$name is away ($message)"
  val CANNOT_BAN_OPERATOR = "You can't ban a channel operator."
  val CANNOT_KICK_OPERATOR = "You can't kick a channel operator."
  val CHANNEL_FULL = "Channel is full."
  val CHANNEL_INFO = (name: String, size: Int) => s"$name with $size user${if (size != 1) "s" else ""}."
  val CHANNEL_LIST = (size: Int) => s"Listing $size channel${if (size != 1) "s" else ""}:"
  val CHANNEL_NOT_EXIST = Array(
    "That channel does not exist.",
    "(If you are trying to search for a user, use the /whois command.)"
  )
  val CHANNEL_RESTRICTED = "That channel is restricted."
  val DND_CANCELLED = "Do Not Disturb mode cancelled."
  val DND_DEFAULT_MSG = "Not available"
  val DND_ENGAGED = "Do Not Disturb mode engaged."
  val DND_UNAVAILABLE = (name: String, message: String) => s"$name is unavailable ($message)"
  val INVALID_COMMAND = "That is not a valid command. Type /help or /? for more info."
  val INVALID_USER = "Invalid user."
  val NO_CHANNEL_INPUT = "What channel do you want to join?"
  val NO_CHAT_PRIVILEGES = "This channel does not have chat privileges."
  val NO_MESSAGE_INPUT = "What do you want to say?"
  val NO_WHISPER_USER_INPUT = "Who do you want to whisper?"
  val NOT_ALLOWED_TO_VIEW = "You do not have permission to view that channel."
  val NOT_BANNED = "That user is not banned."
  val NOT_OPERATOR = "You are not a channel operator."
  val PLACED = (place: Int) => s"You placed $place on the server."
  val PUBLIC_CHANNEL = "This is a chat channel. No Ops will be given."

  val USER_BANNED = (banning: String, banned: String, message: String) => s"$banned was banned by $banning${if (message.nonEmpty) s" ($message)" else ""}."
  val USER_DESIGNATED = (designated: String) => s"$designated is your new designated heir."
  val USER_KICKED = (kicking: String, kicked: String, message: String) => s"$kicked was kicked out of the channel by $kicking${if (message.nonEmpty) s" ($message)" else ""}."
  val USER_NOT_LOGGED_ON = "That user is not logged on."
  val USER_SQUELCHED = (squelched: String) => s"$squelched has been squelched."
  val USER_UNBANNED = (unbanning: String, unbanned: String) => s"$unbanned was unbanned by $unbanning."
  val USER_UNSQUELCHED = (unsquelched: String) => s"$unsquelched has been unsquelched."
  val USERS = (localUsersCount: Int, allUsersCount: Int) =>
    s"There ${if (localUsersCount != 1) s"are $localUsersCount users" else s"is $localUsersCount user"} on this server and $allUsersCount ${addS(allUsersCount, "user")} on $VILE_NET."
  val YOU_KICKED = (kicking: String) => s"$kicking kicked you out of the channel!"
  val YOU_BANNED = "You are banned from that channel."
  val YOU_CANT_SQUELCH = "You can't squelch yourself."
  val WHO_CHANNEL = (name: String) => s"Users in channel $name:"

  val WHOAMI = (username: String, client: String, channel: String) => s"You are $username, using $client in the channel $channel."
  val TOP_LIST = (username: String, client: String) => s"$username was using $client."

  val THE_VOID = "The Void"

  val TELNET_CONNECTED = (address: String) => s"ViLeNet Telnet Connection from [$address]"
  val TELNET_INCORRECT_PASSWORD = "Incorrect password."
  val TELNET_INCORRECT_USERNAME = "Incorrect username."

  val ACCOUNT_ALREADY_EXISTS = (name: String) => s"Account $name already exists."
  val ACCOUNT_CREATED = (name: String, passwordHash: Array[Byte]) => s"Created account $name with password hash ${getStringFromHash(passwordHash)}."
  val NO_ACCOUNT_INPUT = "What account do you want to make?"
  val NO_PASSWORD_INPUT = "You did not enter a password."

  def encodeClient(client: String) = {
    (client: @switch) match {
      case "CHAT" | "TAHC" => "a Chat Client"
      case "LTRD" => "Diablo"
      case "RHSD" => "Diablo Shareware"
      case "VD2D" => "Diablo II"
      case "PX2D" => "Diablo II Lord of Destruction"
      case "RATS" => "Starcraft"
      case "PXES" => "Starcraft Broodwar"
      case "RTSJ" => "Starcraft Japanese"
      case "RHSS" => "Starcraft Shareware"
      case "NB2W" => "Warcraft II"
      case "3RAW" => "Warcraft III"
      case "PX3W" => "Warcraft III The Frozen Throne"
      case _ => "Unknown"
    }
  }

  def addS[A >: Number](number: A, string: String) = {
    if (number != 1) s"${string}s" else string
  }

  def getStringFromHash(hash: Array[Byte]) = {
    hash
    .grouped(4)
      .foldLeft("")((result: String, group: Array[Byte]) =>
        result + group.foldRight("")((b: Byte, result: String) => result + "%02x".format(b))
      )
  }
}
