package com.vilenet.channels

/**
  * Created by filip on 11/25/15.
  */
object PublicChannelActor {
  def apply(name: String) = new PublicChannelActor(name)
}

sealed class PublicChannelActor(channelName: String)
  extends ChattableChannelActor
  with BannableChannelActor
  with NonOperableChannelActor
  with FullableChannelActor
  with RemoteChannelActor {

  override val name = channelName
}
