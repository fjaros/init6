package com.vilenet

import akka.actor.ActorSystem
import com.vilenet.Constants._

/**
 * Created by filip on 9/19/15.
 */
private[vilenet] trait ViLeNetComponent {

  implicit val system = SystemContext()

  lazy val serverColumbus = system.actorSelection(s"/user/$VILE_NET_SERVERS_PATH")
  lazy val channelsActor = system.actorSelection(s"/user/$VILE_NET_CHANNELS_PATH")
  lazy val usersActor = system.actorSelection(s"/user/$VILE_NET_USERS_PATH")
}

private[vilenet] object SystemContext {
  lazy val system = ActorSystem(Constants.VILE_NET)

  def apply() = system
}
