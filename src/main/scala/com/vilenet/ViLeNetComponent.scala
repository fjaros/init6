package com.vilenet

import java.io.File
import java.util.concurrent.TimeUnit

import akka.actor.ActorSystem
import com.typesafe.config.ConfigFactory
import com.vilenet.Constants._

import scala.concurrent.duration.Duration

/**
 * Created by filip on 9/19/15.
 */
private[vilenet] trait ViLeNetComponent {

  implicit val system = SystemContext.system

  lazy val serverColumbus = system.actorSelection(s"/user/$VILE_NET_SERVERS_PATH")
  lazy val channelsActor = system.actorSelection(s"/user/$VILE_NET_CHANNELS_PATH")
  lazy val usersActor = system.actorSelection(s"/user/$VILE_NET_USERS_PATH")
}

private[vilenet] object SystemContext {

  private val start = System.currentTimeMillis()
  lazy val system = ActorSystem(Constants.VILE_NET, ConfigFactory.parseFile(new File("akka.conf")))

  def getUptime = Duration(System.currentTimeMillis() - start, TimeUnit.MILLISECONDS)
}
