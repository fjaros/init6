package com.vilenet.coders.commands

import com.vilenet.Constants._
import com.vilenet.channels.{UserInfo, User}

/**
  * Created by filip on 12/16/15.
  */
object WhoamiCommand {

  def apply(user: User): Command = {
    UserInfo(WHOAMI(user.name, encodeClient(user.client), user.channel))
  }
}
