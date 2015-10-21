package com.vilenet

import akka.actor.{ActorLogging, Actor}

import com.vilenet.Constants._

/**
 * Created by filip on 9/19/15.
 */
private[vilenet] trait ViLeNetActor extends Actor with ActorLogging with ViLeNetComponent {

//  lazy val serverColumbus = system.actorSelection(s"/user/$VILE_NET_SERVERS_PATH")
//  lazy val channelsActor = system.actorSelection(s"/user/$VILE_NET_CHANNELS_PATH")
//  lazy val usersActor = system.actorSelection(s"/user/$VILE_NET_USERS_PATH")
}
