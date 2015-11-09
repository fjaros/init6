package com.vilenet.servers


import akka.actor.{Props, ActorRef}
import com.vilenet.Constants._
import com.vilenet.{ViLeNetComponent, ViLeNetActor}

import scala.collection.mutable

/**
 * Created by filip on 10/17/15.
 */
case object SendBirth

case object ServerOnline
case class ServerOnline(actor: ActorRef)
case object ServerOnlineAck

case object ServerOffline
case class ServerOffline(actor: ActorRef)

case object AddListener

case object SplitMe


object ServerColumbus extends ViLeNetComponent {
  def apply(remoteServer: String) = system.actorOf(Props(new ServerColumbus(Array(remoteServer))), VILE_NET_SERVERS_PATH)
  def apply(remoteServers: Array[String]) = system.actorOf(Props(new ServerColumbus(remoteServers)), VILE_NET_SERVERS_PATH)
}

class ServerColumbus(remoteServers: Array[String]) extends ViLeNetActor {

  val buildPath = (server: String) => s"akka.tcp://$VILE_NET@$server/user/$VILE_NET_SERVERS_PATH"

  var servers = mutable.HashSet[ActorRef]()
  var listeners = mutable.HashSet[ActorRef]()


  override def preStart(): Unit = {
    super.preStart()

    // WTF
    //Thread.sleep(3000)
    self ! SendBirth
  }


  override def receive: Receive = {
    case SendBirth =>
      remoteServers.foreach(server => system.actorSelection(buildPath(server)) ! ServerOnline)

    case ServerOnline =>
      listeners.foreach(_ ! ServerOnline(sender()))
      servers += sender()
      sender() ! ServerOnlineAck

    case ServerOnlineAck =>
      listeners.foreach(_ ! ServerOnline(sender()))
      servers += sender()


    case ServerOffline =>
      servers -= sender()
      listeners.foreach(_ ! ServerOffline(sender()))
      sendServersOffline()

    case AddListener =>
      listeners += sender()
      sendServersOnline()

    case SplitMe =>
      sendServersOffline()
  }


  def sendServersOnline() =
    servers
      .filter(sender() != _)
      .foreach(sender() ! ServerOnline(_))

  def sendServersOffline() =
    servers
      .foreach(sender() ! ServerOffline(_))
}
