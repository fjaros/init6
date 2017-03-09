package com.init6

import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

import akka.actor.ActorSystem
import com.init6.Constants._

import scala.concurrent.duration.Duration

/**
 * Created by filip on 9/19/15.
 */
private[init6] trait Init6Component {

  implicit val system = SystemContext.system

  val daoActor = system.actorSelection(s"/user/$INIT6_DAO_PATH")
  val channelsActor = system.actorSelection(s"/user/$INIT6_CHANNELS_PATH")
  val usersActor = system.actorSelection(s"/user/$INIT6_USERS_PATH")
  val ipLimiterActor = system.actorSelection(s"/user/$INIT6_IP_LIMITER_PATH")
  val topCommandActor = system.actorSelection(s"/user/$INIT6_TOP_COMMAND_ACTOR")
  val serverRegistry = system.actorSelection(s"/user/$INIT6_SERVER_REGISTRY_PATH")

  val lesserEvilAgg = system.actorSelection("/user/lesserevil")

  val remoteAddress = (node: String, path: String) => s"akka://$INIT6@$node/user/$path"

  def getAcceptingUptime = {
    Duration(System.nanoTime - SystemContext.startAccepting, TimeUnit.NANOSECONDS)
  }

  def setAcceptingUptime() = {
    SystemContext.synchronized {
      if (SystemContext.startAccepting == 0) {
        SystemContext.startAccepting = System.nanoTime
      }
    }
  }

  def getAndIncreasePlace = {
    SystemContext.placeCounter.getAndAdd(1)
  }
}

private object SystemContext {

  // Set akka config options from init6.conf
  sys.props += "akka.remote.artery.canonical.hostname" -> Config().Server.akka_host
  sys.props += "akka.remote.artery.canonical.port" -> Config().Server.akka_port.toString

  val system = ActorSystem(Constants.INIT6, Config.load("akka.conf"))

  val startMillis = System.currentTimeMillis()
  private val start = System.nanoTime()
  def getUptime = Duration(System.nanoTime() - start, TimeUnit.NANOSECONDS)

  var startAccepting: Long = 0

  val placeCounter = new AtomicInteger(1)
}
