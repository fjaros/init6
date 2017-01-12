package com.init6.coders.commands

import com.init6.Constants._
import com.init6.channels.{UserInfo, User}

/**
  * Created by filip on 12/16/15.
  */
object WhoamiCommand {

  def apply(user: User): Command = {
    UserInfo(WHOAMI(user.name, encodeClient(user.client), user.inChannel))
  }
}
