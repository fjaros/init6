package com.vilenet.channels

import com.vilenet.coders.commands.{EmoteCommand, ChatCommand}

/**
  * Created by filip on 11/26/15.
  */
trait RemoteChattableChannelActor extends RemoteChannelActor {

  def onChatMessage(user: User, message: String) = remoteUsers ! ChatCommand(user, message)
  def onEmoteMessage(user: User, message: String) = remoteUsers ! EmoteCommand(user, message)
}
