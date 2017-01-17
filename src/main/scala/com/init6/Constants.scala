package com.init6

import java.net.InetSocketAddress
import java.text.DecimalFormat

/**
 * Created by filip on 10/2/15.
 */
object Constants {

  val CHARSET = "windows-1252"

  val TOPIC_DAO = "dao"
  val TOPIC_ONLINE = "online"
  val TOPIC_CHANNELS = "channels"
  val TOPIC_USERS = "users"
  val TOPIC_SPLIT = "split"

  val INIT6 = "init6"
  val INIT6_DAO_PATH = "DAO"
  val INIT6_SERVERS_PATH = "Servers"
  val INIT6_SERVER_REGISTRY_PATH = "ServerRegistry"
  val INIT6_CHANNELS_PATH = "Channels"
  val INIT6_USERS_PATH = "Users"
  val INIT6_IP_LIMITER_PATH = "IpLimiter"
  val INIT6_TOP_COMMAND_ACTOR = "TopCommand"
  val CHANNEL_DISPATCHER = "channel-dispatcher"
  val CHANNELS_DISPATCHER = "channels-dispatcher"
  val SERVER_REGISTRY_DISPATCHER = "server-registry-dispatcher"


  val ALREADY_OPERATOR = "That user is already an operator."
  val AWAY_ENGAGED = "You are now marked as being away."
  val AWAY_CANCELLED = "You are no longer marked as away."
  val AWAY_UNAVAILABLE = (name: String, message: String) => s"$name is away ($message)"
  val CANNOT_BAN_OPERATOR = "You can't ban a channel operator."
  val CANNOT_KICK_OPERATOR = "You can't kick a channel operator."
  val CHANNEL_FULL = "Channel is full."
  val CHANNEL_INFO = (name: String, size: Int, topic: String, creationTime: Long) =>
    String.format("%1$-15s| %2$-5s| %3$-10s| %4$s", name, size.toString, formatNanos(creationTime), topic)

  val CHANNEL_LIST = (size: Int) => s"Listing $size ${addS(size, "channel")}:"
  val CHANNEL_LIST_EMPTY = "There are no visible channels."
  val CHANNEL_NOT_EXIST = Array(
    "That channel does not exist.",
    "(If you are trying to search for a user, use the /whois command.)"
  )
  val CHANNEL_TOPIC = (topic: String) => s"Topic: $topic"
  val CHANNEL_RESTRICTED = "That channel is restricted."
  val CHANNEL_FAILED_TO_JOIN = (channel: String) => s"Failed to join channel $channel"
  val DND_CANCELLED = "Do Not Disturb mode cancelled."
  val UNAVAILABLE_DEFAULT_MSG = "Not available"
  val DND_ENGAGED = "Do Not Disturb mode engaged."
  val DND_UNAVAILABLE = (name: String, message: String) => s"$name is unavailable ($message)"
  val FLOODED_OFF = "You have been disconnected for flooding!"
  val INVALID_COMMAND = "That is not a valid command. Type /help or /? for more info."
  val INVALID_USER = "Invalid user."
  val NO_CHANNEL_INPUT = "What channel do you want to join?"
  val NO_CHAT_PRIVILEGES = "This channel does not have chat privileges."
  val NO_MESSAGE_INPUT = "What do you want to say?"
  val NO_WHISPER_USER_INPUT = "Who do you want to whisper?"
  val NOT_ALLOWED_TO_VIEW = "You do not have permission to view that channel."
  val NOT_BANNED = "That user is not banned."
  val NOT_OPERATOR = "You are not a channel operator."
  val PLACED = (place: Int, serverIp: String) => s"You placed $place on server $serverIp."
  val USER_PLACED = (username: String, place: Int, serverIp: String) => s"$username placed $place on server $serverIp."
  val PUBLIC_CHANNEL = "This is a chat channel. No Ops will be given."

  val SET_TOPIC = (name: String, topic: String) => s"$name ${if (topic.nonEmpty) s"set the topic to: $topic" else "unset the topic."}"

  val UNKNOWN = "Unknown"
  val USER_BANNED = (banning: String, banned: String, message: String) => s"$banned was banned by $banning${if (message.nonEmpty) s" ($message)" else ""}."
  val USER_DESIGNATED = (designated: String) => s"$designated is your new designated heir."
  val USER_KICKED = (kicking: String, kicked: String, message: String) => s"$kicked was kicked out of the channel by $kicking${if (message.nonEmpty) s" ($message)" else ""}."
  val USER_MUTED = (muted: String, channel: String) => s"$muted has been muted in the channel $channel."
  val USER_NOT_LOGGED_ON = "That user is not logged on."
  val USER_SQUELCHED = (squelched: String) => s"$squelched has been squelched."
  val USER_UNBANNED = (unbanning: String, unbanned: String) => s"$unbanned was unbanned by $unbanning."
  val USER_UNMUTED = (unmuted: String, channel: String) => s"$unmuted has been unmuted in the channel $channel."
  val USER_UNSQUELCHED = (unsquelched: String) => s"$unsquelched has been unsquelched."
  val USERS_TOTAL = (allUsersCount: Int, serverIp: String) => s"There ${if (allUsersCount != 1) s"are $allUsersCount users" else s"is $allUsersCount user"} on init 6. You are on server $serverIp."
  val USERS = (localUsersCount: Int, serverIp: String) =>
    String.format("%1$-13s| %2$-5s", serverIp, localUsersCount.toString)

  val YOU_KICKED = (kicking: String) => s"$kicking kicked you out of the channel!"
  val YOU_BANNED = "You are banned from that channel."
  val YOU_CANT_SQUELCH = "You can't squelch yourself."
  val WHO_CHANNEL = (name: String, size: Int) => s"Listing $size ${addS(size, "user")} in channel $name:"

  val WHOAMI = (username: String, client: String, channel: String) => s"You are $username, using $client in the channel $channel."
  val TOP_INFO = (number: Int, protocol: String) => s"Showing the top $number $protocol connections:"
  val TOP_LIST = (number: Int, username: String, client: String, channel: String, loggedInTime: Long) =>
    String.format("%1$-3s| %2$-16s| %3$-5s| %4$-10s| %5$s", number.toString, username, client.reverse, formatNanos(loggedInTime), channel)

  val THE_VOID = "The Void"

  val TELNET_CONNECTED = (address: InetSocketAddress) => s"$INIT6 Telnet Connection from [${address.getAddress.getHostAddress}:${address.getPort}]"
  val TELNET_INCORRECT_PASSWORD = "Incorrect password."
  val TELNET_INCORRECT_USERNAME = "Incorrect username."

  val ACCOUNT_ALREADY_EXISTS = (name: String) => s"Account $name already exists."
  val ACCOUNT_TOO_SHORT = s"Account is too short."
  val ACCOUNT_TOO_LONG = s"Account is too long."
  val ACCOUNT_CLOSED = (name: String, reason: String) => s"Account $name has been closed with reason: $reason"
  val ACCOUNT_OPENED = (name: String) => s"Account $name has been opened."
  val ACCOUNT_CONTAINS_ILLEGAL = s"Account contains illegal characters."
  val ACCOUNT_CREATED = (name: String, passwordHash: Array[Byte]) => s"Created account $name with password hash ${getStringFromHash(passwordHash)}."
  val ACCOUNT_UPDATED = (name: String, passwordHash: Array[Byte]) => s"Changed password of account $name to hash ${getStringFromHash(passwordHash)}."
  val NO_ACCOUNT_INPUT = "What account do you want to make?"
  val NO_PASSWORD_INPUT = "You did not enter a password."

  def isChatProtocol(client: String) = {
    client match {
      case "CHAT" | "TAHC" => true
      case _ => false
    }
  }

  def encodeClient(client: String) = {
    client match {
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

  def getStringFromArray(data: Array[Byte]): String = {
    data.map("%02x".format(_)).mkString
  }

  def getStringFromHash(hash: Array[Byte]) = {
    hash
    .grouped(4)
      .foldLeft("")((result: String, group: Array[Byte]) =>
        result + group.foldRight("")((b: Byte, result: String) => result + "%02x".format(b))
      )
  }

  val decimalFormat = new DecimalFormat("#.000")
  def formatNanos(nanos: Long) = {
    val truncated = math.round(nanos.toDouble / 1000).toDouble / 1000

    if (truncated >= 1000) {
      // seconds
      decimalFormat.format(truncated / 1000) + "s"
    } else {
      // milliseconds
      decimalFormat.format(truncated) + "ms"
    }
  }
}
