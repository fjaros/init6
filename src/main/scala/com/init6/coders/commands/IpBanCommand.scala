package com.init6.coders.commands

import com.init6.servers.Remotable

/**
  * Created by fjaros on 3/3/17.
  */
object IpBanCommand {

  def apply(message: String): Command = {
    val (ip, howLong) = CommandDecoder.spanBySpace(message)
    IpBanCommand(ip.getBytes, System.currentTimeMillis + (howLong.toInt * 60000))
  }
}

case class IpBanCommand(ipAddress: Array[Byte], until: Long) extends Command with Remotable
