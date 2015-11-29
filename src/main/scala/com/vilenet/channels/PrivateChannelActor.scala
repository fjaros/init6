package com.vilenet.channels

/**
  * Created by filip on 11/12/15.
  */
object PrivateChannelActor {
  def apply(name: String) = new PrivateChannelActor(name)
}

sealed class PrivateChannelActor(channelName: String)
  extends ChattableChannelActor
  with BannableChannelActor
  with OperableChannelActor // To op if empty
  with NonOperableChannelActor // To de op
  with FullableChannelActor {

  override val name = channelName
}
