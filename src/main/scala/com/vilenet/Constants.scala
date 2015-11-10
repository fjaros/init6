package com.vilenet

/**
 * Created by filip on 10/2/15.
 */
object Constants {

  val VILE_NET = "ViLeNet"
  val VILE_NET_SERVERS_PATH = "Servers"
  val VILE_NET_CHANNELS_PATH = "Channels"
  val VILE_NET_USERS_PATH = "Users"

  val USER_NOT_LOGGED_ON = "That user is not logged on."

  val MOTD = Array(
  " ===== Welcome to ViLeNet ===== ",
  "Your alpha testing is awesomely appreciated. Here is a cookie jar.",
  "Please feel free to load bots on BOTH servers - 54.193.49.146:6112 AND 54.193.49.146:6113",
  "REMEMBER connecting with the same name twice will result in ghosting. Support for dropping previous name is not yet implemented.",
  "",
  "LATEST PATCH INFO:",
  "- Fixed issue with designates between servers so that an Op on 1 server sees that there may be another Op on 2nd server (after a split).",
  "- Channel ViLe has been turned into a Public Chat channel. No Ops given.",
  "- Implemented The Void channel",
  "- Implemented /kick command",
  "- Added support for more binary bots.",
  "",
  " ===== Join us in channel ViLe ===== ",
  "",
  " ~ l2k-Shadow and rest of the ViLe crew",
  ""
  )
}
