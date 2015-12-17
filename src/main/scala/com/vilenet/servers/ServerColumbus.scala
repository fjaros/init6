package com.vilenet.servers


import akka.actor.{Terminated, Props, ActorRef}
import com.vilenet.Constants._
import com.vilenet.coders.commands.Command
import com.vilenet.{ViLeNetComponent, ViLeNetActor}

import scala.collection.mutable

/**
 * Created by filip on 10/17/15.
 */
case object SendBirth extends Command

case object ServerOnline extends Command
case class ServerOnline(actor: ActorRef) extends Command
case object ServerOnlineAck extends Command

case object ServerOffline extends Command
case class ServerOffline(actor: ActorRef) extends Command

case object AddListener extends Command

case object SplitMe extends Command


object ServerColumbus extends ViLeNetComponent {
  def apply(remoteServer: String): ActorRef = ServerColumbus(Array(remoteServer))
  def apply(remoteServers: Array[String]) = system.actorOf(Props(new ServerColumbus(remoteServers)), VILE_NET_SERVERS_PATH)
}

class ServerColumbus(remoteServers: Array[String]) extends ViLeNetActor {

  val buildPath = (server: String) => s"akka.tcp://$VILE_NET@$server/user/$VILE_NET_SERVERS_PATH"

  var servers = mutable.HashSet[ActorRef]()
  var listeners = mutable.HashSet[ActorRef]()


  override def preStart(): Unit = {
    super.preStart()

    // WTF
    self ! SendBirth
  }


  override def receive: Receive = {
    case SendBirth =>
      remoteServers.foreach(server => system.actorSelection(buildPath(server)) ! ServerOnline)

    case ServerOnline =>
      val remoteServer = sender()
      context.watch(remoteServer)
      listeners.foreach(_ ! ServerOnline(remoteServer))
      servers += remoteServer
      remoteServer ! ServerOnlineAck

    case ServerOnlineAck =>
      listeners.foreach(_ ! ServerOnline(sender()))
      servers += sender()


    case ServerOffline =>
      val remoteServer = sender()
      context.unwatch(remoteServer)
      servers -= remoteServer
      listeners.foreach(_ ! ServerOffline(remoteServer))
      sendServersOffline(remoteServer)

    case Terminated(actor) =>
      val remoteServer = sender()
      context.unwatch(remoteServer)
      servers -= remoteServer
      listeners.foreach(_ ! ServerOffline(remoteServer))
      sendServersOffline(remoteServer)

    case AddListener =>
      listeners += sender()
      sendServersOnline(sender())

    case SplitMe =>
      sendServersOffline(sender())
  }


  def sendServersOnline(remoteServer: ActorRef) =
    servers
      .filterNot(_ == remoteServer)
      .foreach(remoteServer ! ServerOnline(_))

  def sendServersOffline(remoteServer: ActorRef) =
    servers
      .foreach(remoteServer ! ServerOffline(_))
}
