package com.vilenet.channels

/**
  * Created by filip on 11/25/15.
  */
object PublicChannelActor {
  def apply(name: String) = new PublicChannelActor(name)
}

sealed class PublicChannelActor(override val name: String)
  extends PublicLimitlessChannelActor(name)
  with FullableChannelActor
