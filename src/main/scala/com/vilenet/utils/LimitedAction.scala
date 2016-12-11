package com.vilenet.utils

import java.util.concurrent.TimeUnit

import scala.concurrent.duration.Duration

/**
  * Created by filip on 12/13/15.
  */
object LimitedAction {
  def apply(): LimitedAction = LimitedAction(Duration(60, TimeUnit.SECONDS), 1)
  def apply(limitedFor: Duration, maxTimes: Int) = new LimitedAction(limitedFor, maxTimes)
}

sealed class LimitedAction(limitedFor: Duration, maxTimes: Int) {

  private val limitedMillis = limitedFor.toMillis

  private var timestamp: Long = _
  private var currentTimes: Int = _

  private def isReady: Boolean = {
    if (timestamp != 0) {
      if (maxTimes > currentTimes) {
        currentTimes += 1
        timestamp = System.currentTimeMillis()
        true
      } else if (System.currentTimeMillis() - timestamp >= limitedMillis) {
        currentTimes = 0
        timestamp = System.currentTimeMillis()
        true
      } else {
        false
      }
    } else {
      timestamp = System.currentTimeMillis()
      true
    }
  }

  @inline final def foreach[A](ifReady: => A): Unit =
    if (isReady) ifReady

  @inline final def fold[A](ifNotReady: => A)(ifReady: => A): A =
    if (isReady) ifReady else ifNotReady
}
