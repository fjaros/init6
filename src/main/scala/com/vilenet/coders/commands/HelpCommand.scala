package com.vilenet.coders.commands

import com.vilenet.channels.UserInfoArray

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
      "/channels, /list",
      "/motd",

      "/help, /?"
    )
  )
}
