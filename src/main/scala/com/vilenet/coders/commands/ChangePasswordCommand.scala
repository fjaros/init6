package com.vilenet.coders.commands

import com.vilenet.Constants._
import com.vilenet.channels.UserInfo
import com.vilenet.coders.binary.hash.BSHA1

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
