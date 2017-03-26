package com.init6.servers

import java.util.concurrent.TimeUnit

import akka.actor.{Cancellable, Props}
import com.init6.Constants._
import com.init6.coders.commands.{BroadcastCommand, Command}
import com.init6.{Config, Init6Actor, Init6Component}

import scala.concurrent.duration.{Duration, FiniteDuration}

/**
  * Created by fjaros on 3/24/17.
  */
object ServerAnnouncementActor extends Init6Component {
  def apply(timeToDrop: Long) =
    system.actorOf(Props(classOf[ServerAnnouncementActor], timeToDrop), INIT6_SERVER_ANNOUNCEMENT_PATH)
}

case class RepeatingAnnoucement(message: String, duration: FiniteDuration) extends Command

class ServerAnnouncementActor(timeToDrop: Long) extends Init6Actor {

  var announcement: Option[Cancellable] = None

  override def preStart() = {
    super.preStart()

    if (!Config().Server.Chat.enabled) {
      import context.dispatcher
      system.scheduler.scheduleOnce(Duration(
        timeToDrop - 15, TimeUnit.SECONDS
      ))({
        usersActor ! BroadcastCommand(WILL_DROP_IN(Config().Server.host, 15))
      })
    }
  }

  override def receive = {
    case RepeatingAnnoucement(message, duration) =>
      import context.dispatcher
      if (announcement.isDefined) {
        announcement.get.cancel()
      }

      announcement = Some(system.scheduler.schedule(
        Duration(0, TimeUnit.MILLISECONDS),
        duration
      )({
        usersActor ! BroadcastCommand(message)
      }))
  }
}
