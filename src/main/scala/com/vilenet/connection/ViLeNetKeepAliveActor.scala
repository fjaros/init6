package com.vilenet.connection

import java.util.concurrent.{Executors, TimeUnit}

import akka.actor.{ActorRef, PoisonPill}
import com.vilenet.ViLeNetActor

/**
  * Created by filip on 12/29/15.
  */
trait ViLeNetKeepAliveActor extends ViLeNetActor {

  private val keepAliveExecutor = Executors.newSingleThreadScheduledExecutor()
  protected var keptAlive = true

  override def postStop(): Unit = {
    keepAliveExecutor.shutdown()

    super.postStop()
  }

  def keepAlive(actor: ActorRef, f: () => Unit): Unit = {
    keepAlive(actor, f, 1, TimeUnit.MINUTES)
  }

  def keepAlive(actor: ActorRef, f: () => Unit, delay: Long, unit: TimeUnit): Unit = {
    keepAliveExecutor.scheduleWithFixedDelay(new Runnable {
      override def run(): Unit = {
        if (keptAlive) {
          keptAlive = false
          f()
        } else {
          actor ! PoisonPill
        }
      }
    }, delay, delay, unit)
  }
}
