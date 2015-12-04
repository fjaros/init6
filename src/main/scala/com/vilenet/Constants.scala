package com.vilenet

/**
 * Created by filip on 10/2/15.
 */
object Constants {

  val VILE_NET = "ViLeNet"
  val VILE_NET_SERVERS_PATH = "Servers"
  val VILE_NET_CHANNELS_PATH = "Channels"
  val VILE_NET_USERS_PATH = "Users"
  val CHANNEL_DISPATCHER = "channel-dispatcher"


  val CANNOT_BAN_OPERATOR = "You can't ban a channel operator."
  val CHANNEL_FULL = "Channel is full."
  val CHANNEL_RESTRICTED = "That channel is restricted."
  val INVALID_COMMAND = "That is not a valid command. Type /help or /? for more info."
  val INVALID_USER = "Invalid user."
  val NO_CHANNEL_INPUT = "What channel do you want to join?"
  val NOT_BANNED = "That user is not banned."
  val NOT_OPERATOR = "You are not a channel operator."

  val USER_NOT_LOGGED_ON = "That user is not logged on."

  val USER_DESIGNATED = (designated: String) => s"$designated is your new designated heir."
  val USER_KICKED = (kicking: String, kicked: String) => s"$kicked was kicked out of the channel by $kicking."
  val USER_BANNED = (banning: String, banned: String) => s"$banned was banned by $banning."
  val USER_UNBANNED = (unbanning: String, unbanned: String) => s"$unbanned was unbanned by $unbanning."
  val USER_SQUELCHED = (squelched: String) => s"$squelched has been squelched."
  val USER_UNSQUELCHED = (unsquelched: String) => s"$unsquelched has been unsquelched."
  val YOU_KICKED = (kicking: String) => s"$kicking kicked you out of the channel!"
  val YOU_BANNED = "You are banned from that channel."
  val YOU_CANT_SQUELCH = "You can't squelch yourself."

  val WHOAMI = (username: String, client: String, channel: String) => s"You are $username, using $client in the channel $channel."

  val THE_VOID = "The Void"


  val MOTD = Array(
  " ===== Welcome to ViLeNet ===== ",
  "Your alpha testing is awesomely appreciated. Here is a cookie jar.",
  "Please feel free to load bots on BOTH servers - 54.193.49.146:6112 AND 54.193.49.146:6113",
  "REMEMBER connecting with the same name twice will result in ghosting. Support for dropping previous name is not yet implemented.",
  "",
  "LATEST PATCH INFO:",
  " ~~~ ",
  " ^ _ ^ SQUELCH IMPLEMENTED ^ _ ^",
  " ~~~ ",
  "",
  " ===== Join us in channel ViLe ===== ",
  "",
  " ~ l2k-Shadow and rest of the ViLe crew",
  ""
  )
}
