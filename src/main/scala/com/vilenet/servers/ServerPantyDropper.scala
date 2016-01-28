package com.vilenet.servers

import java.util.concurrent.TimeUnit

import akka.actor.Props
import akka.cluster.pubsub.DistributedPubSubMediator.Publish
import akka.util.Timeout
import com.vilenet.Constants._
import com.vilenet.coders.commands.{BroadcastCommand, Command}
import com.vilenet.{ViLeNetClusterActor, ViLeNetComponent}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.Random

/**
 * Created by filip on 10/17/15.
 */
case object SendBirth extends Command

case object ServerOnline extends Command
case object SplitMe extends Command


object ServerPantyDropper extends ViLeNetComponent {
  def apply(serverName: String) = system.actorOf(Props(new ServerPantyDropper(serverName)), VILE_NET_SERVERS_PATH)
}

case object AnnounceSplit extends Command
case object Split extends Command
case object Recon extends Command

class ServerPantyDropper(serverName: String) extends ViLeNetClusterActor {

  val buildPath = (server: String) => s"akka.tcp://$VILE_NET@$server/user/$VILE_NET_SERVERS_PATH"

  val random = new Random(System.currentTimeMillis())

  system.scheduler.scheduleOnce(Timeout(45 + random.nextInt(150), TimeUnit.MINUTES).duration, self, AnnounceSplit)

  override def receive: Receive = {
    case AnnounceSplit =>
      usersActor ! BroadcastCommand(s">>> $serverName is going to split from ViLeNet in 10 seconds!")
      system.scheduler.scheduleOnce(Timeout(10, TimeUnit.SECONDS).duration, self, Split)

    case Split =>
      mediator ! Publish(TOPIC_SPLIT, SplitMe)
      system.scheduler.scheduleOnce(Timeout(15 + random.nextInt(45), TimeUnit.SECONDS).duration, self, Recon)

    case Recon =>
      mediator ! Publish(TOPIC_ONLINE, ServerOnline)
      usersActor ! BroadcastCommand(s">>> $serverName has reconnected to ViLeNet!")
      system.scheduler.scheduleOnce(Timeout(90 + random.nextInt(300), TimeUnit.MINUTES).duration, self, AnnounceSplit)
  }
}
