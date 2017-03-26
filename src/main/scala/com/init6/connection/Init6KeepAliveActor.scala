package com.init6.connection

import java.util.concurrent.TimeUnit

import akka.actor.{ActorRef, Cancellable}
import com.init6.Init6Actor
import com.init6.users.KillConnection

import scala.concurrent.duration.Duration

/**
  * Created by filip on 12/29/15.
  */
trait Init6KeepAliveActor extends Init6Actor {

  private var pingTask: Cancellable = _
  protected var keptAlive = 0

  override def postStop(): Unit = {
    Option(pingTask).foreach(_.cancel())

    super.postStop()
  }

  def keepAlive(actor: ActorRef, f: () => Unit): Unit = {
    keepAlive(actor, f, 25, TimeUnit.SECONDS)
  }

  def keepAlive(actor: ActorRef, f: () => Unit, delay: Long, unit: TimeUnit): Unit = {
    val pingDuration = Duration(25, TimeUnit.SECONDS)
    import context.dispatcher

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
