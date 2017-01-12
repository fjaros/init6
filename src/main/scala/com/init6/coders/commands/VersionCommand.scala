package com.init6.coders.commands

import com.init6.BuildInfo
import com.init6.Constants._
import com.init6.channels.UserInfoArray

/**
  * Created by fjaros on 12/23/16.
  */
object VersionCommand {

  def apply() = {
    UserInfoArray(Array(
      "Server Information:",
      s"$INIT6 By l2k-Shadow",
      s"Build ${BuildInfo.BUILD_NUMBER}",
      s"Hash ${BuildInfo.BUILD_HASH}"
    ))
  }
}
