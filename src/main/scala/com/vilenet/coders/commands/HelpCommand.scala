package com.vilenet.coders.commands

import com.vilenet.channels.UserInfoArray

/**
  * Created by filip on 12/16/15.
  */
object HelpCommand {

  def apply() = UserInfoArray(
    Array(
      "Avaialble commands:",
      "/whisper, /w, /msg, /m",
      "/channel, /join, /j",
      "/designate",
      "/emote, /me",
      "/whoami",
      "/whois",
      "/away",
      "/dnd",

      "/ban",
      "/unban",
      "/kick",

      "/top, /top chat, /top binary",
      "/channels",
      "/motd",

      "/help, /?"
    )
  )
}
