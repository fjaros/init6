package com.init6.coders.commands

import com.init6.channels.UserInfoArray

/**
  * Created by filip on 12/16/15.
  */
object HelpCommand {

  def apply() = UserInfoArray(
    Array(
      "Available commands:",
      "/makeaccount",
      "/changepassword, /chpass",
      "/whisper, /w, /msg, /m",
      "/channel, /join, /j",
      "/squelch, /ignore",
      "/unsquelch, /unignore",
      "/designate",
      "/emote, /me",
      "/whoami",
      "/whois",
      "/ops",
      "/who",
      "/away",
      "/dnd",

      "/ban",
      "/unban",
      "/kick",

      "/top, /top chat, /top binary",
      "/place <name>",
      "/users",
      "/channels, /chs, /list",
      "/topic <message>",
      "/servermotd, /motd",

      "/serveruptime, /uptime",
      "/serverversion, /version",
      "/help, /?"
    )
  )
}
