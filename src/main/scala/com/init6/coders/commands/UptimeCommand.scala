package com.init6.coders.commands

import com.init6.Constants._
import com.init6.channels.UserInfo
import com.init6.servers.Remotable
import com.init6.{Config, SystemContext}

/**
  * Created by filip on 12/16/15.
  */
case object UptimeCommand extends Command with Remotable {

  def apply() = {
    val uptime = SystemContext.getUptime
    UserInfo(
      s"""Uptime on ${Config().Server.host}: ${
        Seq(
          uptime.toDays -> "day",
          uptime.toHours % 24 -> "hour",
          uptime.toMinutes % 60 -> "minute",
          uptime.toSeconds % 60 -> "second"
        )
          .filter { case (value, _) => value > 0 }
          .map { case (value, text) => s"$value ${addS(value, text)}" }
          .mkString(" ")
      }."""
    )
  }
}
