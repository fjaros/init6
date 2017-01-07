package com.vilenet.channels

/**
  * Created by filip on 1/6/17.
  */
object PublicLimitlessChannelActor {
  def apply(name: String) = new PublicLimitlessChannelActor(name)
}

class PublicLimitlessChannelActor(override val name: String)
  extends ChattableChannelActor
    with BannableChannelActor
    with NonOperableChannelActor
