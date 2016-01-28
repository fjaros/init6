package com.vilenet.connection

import java.util.concurrent.{Executors, TimeUnit}

import akka.actor.ActorRef
import com.vilenet.ViLeNetActor
import com.vilenet.users.KillSelf

/**
  * Created by filip on 12/29/15.
  */
trait ViLeNetKeepAliveActor extends ViLeNetActor {

  private val keepAliveExecutor = Executors.newSingleThreadScheduledExecutor()
  protected var keptAlive = 0

  override def postStop(): Unit = {
    keepAliveExecutor.shutdown()

    super.postStop()
  }

  def keepAlive(actor: ActorRef, f: () => Unit): Unit = {
    keepAlive(actor, f, 25, TimeUnit.SECONDS)
  }

  def keepAlive(actor: ActorRef, f: () => Unit, delay: Long, unit: TimeUnit): Unit = {
    keepAliveExecutor.scheduleWithFixedDelay(new Runnable {
      override def run(): Unit = {
        if (keptAlive < 4) {
          keptAlive += 1
          f()
        } else {
          actor ! KillSelf
        }
      }
    }, delay, delay, unit)
  }
}
