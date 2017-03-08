package com.init6.coders.commands

import com.init6.channels.UserInfoArray

/**
  * Created by filip on 12/16/15.
  */
object HelpCommand {

  def apply() = UserInfoArray(
    Array(
      "Available commands:",
      "/alias, /register",
      "/aliasto, /registerto",
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

      "/friends add, /friends list, /friends msg, /friends remove",

      "/ban",
      "/unban",
      "/kick",

      "/top, /top chat, /top binary",
      "/top sea, /top dal, /top chi",
      "/place <name>",
      "/users",
      "/channels, /chs, /list",
      "/topic <message>",
      "/servermotd, /motd",

      "/serveruptime, /uptime",
      "/serverversion, /version",
      "/serverhelp, /help, /?"
    )
  )
}
