package com.init6.channels.utils

/**
  * Created by filip on 1/12/17.
  */
object ChannelJoinValidator {

  def apply(currentChannel: String, joiningChannel: String) = {
    val lCurrent = currentChannel.toLowerCase
    val lJoining = joiningChannel.toLowerCase

    lCurrent match {
      case "init 6" =>
        lJoining match {
          case "init 6" | "init6" => false
          case _ => true
        }
      case _ =>
        lCurrent != lJoining
    }
  }
}
