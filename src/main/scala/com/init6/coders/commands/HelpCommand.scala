package com.init6.coders.commands

import com.init6.channels.UserInfoArray

/**
  * Created by filip on 12/16/15.
  */
object HelpCommand {

  def apply() = UserInfoArray(
    Array(
      "Avaialble commands:",
      "/makeaccount",
      "/whisper, /w, /msg, /m",
      "/channel, /join, /j",
      "/squelch, /ignore",
      "/unsquelch, /unignore",
      "/designate",
      "/emote, /me",
      "/whoami",
      "/whois",
      "/who",
      "/away",
      "/dnd",

      "/ban",
      "/unban",
      "/kick",

      "/top, /top chat, /top binary",
      "/place",
      "/users",
      "/channels, /list",
      "/motd",

      "/help, /?"
    )
  )
}
