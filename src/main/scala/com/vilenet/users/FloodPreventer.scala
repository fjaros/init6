package com.vilenet.users

import com.vilenet.Config.AntiFlood._

/**
  * Created by fjaros on 12/23/16.
  */
private[users] trait FloodPreventer {

  private case class FloodState(credits: Int, timestamp: Long)
  private var state = FloodState(maxCredits, System.currentTimeMillis())

  def floodState(numBytes: Int): Boolean = {
    val now = System.currentTimeMillis()

    val credits = math.min(
      state.credits + ((now - state.timestamp) / 1000 * creditsReturnedPerSecond),
      maxCredits
    ).toInt

    val cost = math.min(packetMinCost + numBytes * costPerByte, packetMaxCost)

    state = FloodState(credits - cost, now)

    cost > credits
  }
}
