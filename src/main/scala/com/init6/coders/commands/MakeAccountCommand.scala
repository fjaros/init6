package com.init6.coders.commands

import com.init6.Config
import com.init6.Constants._
import com.init6.channels.UserInfo
import com.init6.coders.binary.hash.BSHA1
import com.init6.db.DAO

/**
  * Created by filip on 12/16/15.
  */
object MakeAccountCommand {

  def apply(command: String): Command = {
    val (username, password) = CommandDecoder.spanBySpace(command)

    if (username.nonEmpty) {
      if (password.nonEmpty) {
        if (username.length < Config().Accounts.minLength) {
          return UserInfo(ACCOUNT_TOO_SHORT)
        }

        val maxLenUser = username.take(Config().Accounts.maxLength)
        // Check for illegal characters
        if (maxLenUser.forall(c => Config().Accounts.allowedCharacters.contains(c.toLower))) {
          DAO.getUser(maxLenUser).fold[Command]({
            val passwordHash = BSHA1(password.toLowerCase)
            AccountMade(maxLenUser, passwordHash)
          })(_ => UserInfo(ACCOUNT_ALREADY_EXISTS(maxLenUser)))
        } else {
          UserInfo(ACCOUNT_CONTAINS_ILLEGAL)
        }
      } else {
        UserInfo(NO_PASSWORD_INPUT)
      }
    } else {
      UserInfo(NO_ACCOUNT_INPUT)
    }
  }
}

case class AccountMade(username: String, passwordHash: Array[Byte]) extends Command
