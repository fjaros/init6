package com.init6.servers

import java.util.concurrent.TimeUnit

import akka.actor.Props
import akka.util.Timeout
import com.init6.Constants._
import com.init6.coders.commands.{BroadcastCommand, Command}
import com.init6.{Init6Actor, Init6Component}

import scala.util.Random

/**
 * Created by filip on 10/17/15.
 */
case object SendBirth extends Command

case object ServerOnline extends Command
case object SplitMe extends Command


object ServerPantyDropper extends Init6Component {
  def apply(serverName: String) = system.actorOf(Props(classOf[ServerPantyDropper], serverName), INIT6_SERVERS_PATH)
}

case object AnnounceSplit extends Command
case object Split extends Command
case object Recon extends Command

class ServerPantyDropper(serverName: String) extends Init6Actor {

  import context.dispatcher

  val buildPath = (server: String) => s"akka://$INIT6@$server/user/$INIT6_SERVERS_PATH"

  val random = new Random(System.currentTimeMillis())

  //system.scheduler.scheduleOnce(Timeout(45 + random.nextInt(150), TimeUnit.MINUTES).duration, self, AnnounceSplit)

  override def receive: Receive = {
    case AnnounceSplit =>
      usersActor ! BroadcastCommand(s">>> $serverName is going to split from $INIT6 in 10 seconds!")
      system.scheduler.scheduleOnce(Timeout(10, TimeUnit.SECONDS).duration, self, Split)

    case Split =>
      //mediator ! Publish(TOPIC_SPLIT, SplitMe)
      system.scheduler.scheduleOnce(Timeout(15 + random.nextInt(45), TimeUnit.SECONDS).duration, self, Recon)

    case Recon =>
      //mediator ! Publish(TOPIC_ONLINE, ServerOnline)
      usersActor ! BroadcastCommand(s">>> $serverName has reconnected to $INIT6!")
      system.scheduler.scheduleOnce(Timeout(90 + random.nextInt(300), TimeUnit.MINUTES).duration, self, AnnounceSplit)
  }
}
