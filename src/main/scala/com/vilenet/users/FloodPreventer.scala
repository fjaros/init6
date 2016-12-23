package com.vilenet.users

/**
  * Created by fjaros on 12/23/16.
  */
private[users] trait FloodPreventer {

  /*
    Starting Credits = 1000
    Per Packet = Min(200 + numBytes * 5, 400)
    Credit Return = 100 per second
   */
  private val COST_PER_BYTE = 5
  private val MIN_COST = 200
  private val MAX_COST = 400
  private val MAX_CREDITS = 1000

  private case class FloodState(credits: Int, timestamp: Long)
  private var state = FloodState(MAX_CREDITS, System.currentTimeMillis())

  def floodState(numBytes: Int): Boolean = {
    val now = System.currentTimeMillis()

    // minimum state.credits + 100 per second of passed time or MAX_CREDITS
    val credits = math.min(
      state.credits + ((now - state.timestamp) / 10), MAX_CREDITS
    ).toInt

    val cost = math.min(MIN_COST + numBytes * COST_PER_BYTE, MAX_COST)

    state = FloodState(credits - cost, now)

    cost > credits
  }
}
