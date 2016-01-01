package com.vilenet.coders.commands

import com.vilenet.Constants._
import com.vilenet.SystemContext
import com.vilenet.channels.UserInfo

/**
  * Created by filip on 12/16/15.
  */
object UptimeCommand {

  def apply() = {
    val uptime = SystemContext.getUptime
    UserInfo(
      s"""Uptime: ${
        Array(
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
