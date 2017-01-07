package com.vilenet.connection

import java.util.concurrent.TimeUnit

import akka.actor.{ActorRef, Cancellable}
import com.vilenet.ViLeNetActor
import com.vilenet.users.KillConnection

import scala.concurrent.duration.Duration

/**
  * Created by filip on 12/29/15.
  */
trait ViLeNetKeepAliveActor extends ViLeNetActor {

  private var pingTask: Cancellable = _
  protected var keptAlive = 0

  override def postStop(): Unit = {
    pingTask.cancel()

    super.postStop()
  }

  def keepAlive(actor: ActorRef, f: () => Unit): Unit = {
    keepAlive(actor, f, 25, TimeUnit.SECONDS)
  }

  def keepAlive(actor: ActorRef, f: () => Unit, delay: Long, unit: TimeUnit): Unit = {
    val pingDuration = Duration(25, TimeUnit.SECONDS)
    import system.dispatcher

    pingTask = system.scheduler.schedule(
      pingDuration,
      pingDuration
    )({
        if (keptAlive < 4) {
          keptAlive += 1
          f()
        } else {
          actor ! KillConnection
        }
    })
  }
}
