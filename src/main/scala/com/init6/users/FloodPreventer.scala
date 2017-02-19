package com.init6.users

import com.init6.Config
import com.init6.coders.commands.Command

/**
  * Created by fjaros on 12/23/16.
  */
private[users] trait FloodPreventer {

  private case class FloodState(
    credits: Int = Config().AntiFlood.maxCredits,
    timestamp: Long = System.currentTimeMillis()
  )
  private var state = FloodState()

  def floodState(command: Command, numBytes: Int): Boolean = {
    val cfg = Config().AntiFlood
    if (!cfg.whitelist.contains(command.getClass.getName)) {

      val now = System.currentTimeMillis()

      if (now >= state.timestamp) {
        val credits = math.min(
          state.credits + ((now - state.timestamp) / 1000 * cfg.creditsReturnedPerSecond),
          cfg.maxCredits
        ).toInt

        val cost = math.min(cfg.packetMinCost + numBytes * cfg.costPerByte, cfg.packetMaxCost)

        state = FloodState(credits - cost, now)

        cost > credits
      } else {
        // Server time has changed backwards. Ignore this update.
        state = FloodState()
        false
      }
    } else {
      false
    }
  }
}
