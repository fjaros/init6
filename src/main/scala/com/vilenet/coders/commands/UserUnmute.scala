package com.vilenet.coders.commands

/**
  * Created by filip on 1/9/17.
  */
case class UserUnmute(override val toUsername: String) extends UserToChannelCommand
