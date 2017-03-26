package com.init6.coders.commands

import java.util.concurrent.TimeUnit

import com.init6.servers.RepeatingAnnoucement

import scala.concurrent.duration.Duration

/**
  * Created by fjaros on 3/24/17.
  */
object RepeatingBroadcast {
  def apply(message: String) = {
    val (minutes, rest) = CommandDecoder.spanBySpace(message)

    RepeatingAnnoucement(rest, Duration(minutes.toLong, TimeUnit.MINUTES))
  }
}
