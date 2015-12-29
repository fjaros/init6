package com.vilenet.coders.commands

import com.vilenet.Constants._
import com.vilenet.channels.UserInfo
import com.vilenet.coders.binary.hash.BSHA1
import com.vilenet.db.DAO

/**
  * Created by filip on 12/16/15.
  */
object MakeAccountCommand {

  def apply(command: String) = {
    val (account, password) = CommandDecoder.spanBySpace(command)

    if (account.nonEmpty) {
      if (password.nonEmpty) {
        DAO.getUser(account).fold[Command]({
          val passwordHash = BSHA1(password)
          AccountMade(account, passwordHash)
        })(_ => UserInfo(ACCOUNT_ALREADY_EXISTS(account)))
      } else {
        UserInfo(NO_PASSWORD_INPUT)
      }
    } else {
      UserInfo(NO_ACCOUNT_INPUT)
    }
  }
}

case class AccountMade(username: String, passwordHash: Array[Byte]) extends Command
