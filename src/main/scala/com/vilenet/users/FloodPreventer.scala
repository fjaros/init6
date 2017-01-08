package com.vilenet.users

import com.vilenet.Config

/**
  * Created by fjaros on 12/23/16.
  */
private[users] trait FloodPreventer {

  private case class FloodState(credits: Int, timestamp: Long)
  private var state = FloodState(Config().AntiFlood.maxCredits, System.currentTimeMillis())

  def floodState(numBytes: Int): Boolean = {
    val cfg = Config().AntiFlood
    val now = System.currentTimeMillis()

    val credits = math.min(
      state.credits + ((now - state.timestamp) / 1000 * cfg.creditsReturnedPerSecond),
      cfg.maxCredits
    ).toInt

    val cost = math.min(cfg.packetMinCost + numBytes * cfg.costPerByte, cfg.packetMaxCost)

    state = FloodState(credits - cost, now)

    cost > credits
  }
}
