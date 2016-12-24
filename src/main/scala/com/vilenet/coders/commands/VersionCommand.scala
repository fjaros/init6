package com.vilenet.coders.commands

import com.vilenet.BuildInfo
import com.vilenet.Constants._
import com.vilenet.channels.UserInfoArray

/**
  * Created by fjaros on 12/23/16.
  */
object VersionCommand {

  def apply() = {
    UserInfoArray(Array(
      "Server Information:",
      s"$VILE_NET By l2k-Shadow",
      s"Build ${BuildInfo.BUILD_NUMBER}",
      s"Hash ${BuildInfo.BUILD_HASH}"
    ))
  }
}
