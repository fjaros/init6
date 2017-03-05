package com.init6.coders.commands

import com.init6.coders.IPUtils
import com.init6.servers.Remotable

/**
  * Created by fjaros on 3/5/17.
  */
object UnIpBanCommand {

  def apply(message: String): Command = {
    UnIpBanCommand(IPUtils.stringToBytes(message))
  }
}

case class UnIpBanCommand(ip: Array[Byte]) extends Command with Remotable
