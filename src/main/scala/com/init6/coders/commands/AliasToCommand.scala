package com.init6.coders.commands

import com.init6.Constants._
import com.init6.channels.{User, UserError}
import com.init6.coders.binary.hash.BSHA1
import com.init6.db.{DAO, DAOActor}

/**
  * Created by fjaros on 1/29/17.
  */
object AliasToCommand {

  def apply(user: User, message: String): Command = {
    val (alias, aliasPassword) = CommandDecoder.spanBySpace(message)
    DAO.getUser(alias)
      .fold[Command](UserError(ACCOUNT_NOT_EXIST(alias)))(dbUser => {
      if (BSHA1(aliasPassword, dbUser.password_hash)) {
        AliasToCommand(alias)
      } else {
        UserError(ACCOUNT_INCORRECT_PASSWORD(alias))
      }
    })
  }
}

case class AliasToCommand(alias: String) extends Command
