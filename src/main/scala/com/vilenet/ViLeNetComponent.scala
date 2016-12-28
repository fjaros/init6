package com.vilenet

import java.util.concurrent.TimeUnit

import akka.actor.ActorSystem
import akka.cluster.Cluster
import akka.cluster.pubsub.DistributedPubSub
import com.vilenet.Constants._

import scala.concurrent.duration.Duration

/**
 * Created by filip on 9/19/15.
 */
private[vilenet] trait ViLeNetComponent {

  implicit val system = SystemContext.system

  val channelsActor = system.actorSelection(s"/user/$VILE_NET_CHANNELS_PATH")
  val usersActor = system.actorSelection(s"/user/$VILE_NET_USERS_PATH")
  val ipLimiterActor = system.actorSelection(s"/user/$VILE_NET_IP_LIMITER_PATH")
  val topCommandActor = system.actorSelection(s"/user/$VILE_NET_TOP_COMMAND_ACTOR")
}

private object SystemContext {

  // Set akka config options from vilenet.conf
  sys.props += "akka.remote.artery.canonical.hostname" -> Config.Server.akka_host
  sys.props += "akka.remote.artery.canonical.port" -> Config.Server.akka_port.toString
  Config.Server.akka_nodes
    .zipWithIndex
    .foreach {
      case (node, index) =>
        sys.props += s"akka.cluster.seed-nodes.$index" -> s"akka://$VILE_NET@$node"
    }

  val system = ActorSystem(Constants.VILE_NET, Config.load("akka.conf"))
  val cluster = Cluster(system)
  val mediator = DistributedPubSub(system).mediator

  private val start = System.currentTimeMillis()
  def getUptime = Duration(System.currentTimeMillis() - start, TimeUnit.MILLISECONDS)
}
