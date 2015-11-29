package com.vilenet.channels

import com.vilenet.coders.{EmoteMessage, ChatMessage}

/**
  * Created by filip on 11/26/15.
  */
trait RemoteChattableChannelActor extends RemoteChannelActor {

  def onChatMessage(user: User, message: String) = remoteUsers ! ChatMessage(user, message)
  def onEmoteMessage(user: User, message: String) = remoteUsers ! EmoteMessage(user, message)
}
