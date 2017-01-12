package com.init6.coders.commands

import com.init6.Constants._
import com.init6.channels.UserInfo
import com.init6.coders.binary.hash.BSHA1

/**
  * Created by fjaros on 12/22/16.
  */
object ChangePasswordCommand {

  def apply(newPassword: String): Command = {

    if (newPassword.nonEmpty) {
      ChangePasswordCommand(BSHA1(newPassword))
    } else {
      UserInfo(NO_PASSWORD_INPUT)
    }
  }
}

case class ChangePasswordCommand(passwordHash: Array[Byte]) extends Command
