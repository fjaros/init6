package com.vilenet.servers

import java.util.concurrent.{Callable, TimeUnit, Executors}

import akka.actor.{Terminated, Props, ActorRef}
import akka.cluster.ClusterEvent.MemberUp
import akka.cluster.pubsub.DistributedPubSubMediator.Publish
import com.vilenet.Constants._
import com.vilenet.coders.commands.{BroadcastCommand, Command}
import com.vilenet.{SystemContext, ViLeNetClusterActor, ViLeNetComponent}

import scala.collection.mutable
import scala.util.Random

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
  def apply(serverName: String) = system.actorOf(Props(new ServerColumbus(serverName)), VILE_NET_SERVERS_PATH)
}

class ServerSplitter(serverName: String) extends ViLeNetComponent {

  val cluster = SystemContext.cluster
  val mediator = SystemContext.mediator

  val random = new Random(System.currentTimeMillis())

  val splitExecutor = Executors.newSingleThreadScheduledExecutor()


  def go() = {
    splitExecutor.schedule(AnnounceSplit, 120 + random.nextInt(600), TimeUnit.SECONDS)
  }

  def stop() = {
    splitExecutor.shutdown()
  }

  private object AnnounceSplit extends Runnable {

    override def run(): Unit = {
      usersActor ! BroadcastCommand(s">>> $serverName is going to split from ViLeNet in 10 seconds!")
      splitExecutor.schedule(Split, 10, TimeUnit.SECONDS)
    }
  }

  private object Split extends Runnable {

    override def run(): Unit = {
      mediator ! Publish(TOPIC_SPLIT, SplitMe)
      splitExecutor.schedule(Recon, 15 + random.nextInt(30), TimeUnit.SECONDS)
    }
  }

  private object Recon extends Runnable {

    override def run(): Unit = {
      mediator ! Publish(TOPIC_ONLINE, ServerOnline)
      usersActor ! BroadcastCommand(s">>> $serverName has reconnected to ViLeNet!")
      splitExecutor.schedule(AnnounceSplit, 600 + random.nextInt(1200), TimeUnit.SECONDS)
    }
  }
}

class ServerColumbus(serverName: String) extends ViLeNetClusterActor {

  val buildPath = (server: String) => s"akka.tcp://$VILE_NET@$server/user/$VILE_NET_SERVERS_PATH"

  var servers = mutable.HashSet[ActorRef]()
  var listeners = mutable.HashSet[ActorRef]()

  val serverSplitter = new ServerSplitter(serverName)


  override def postStop(): Unit = {
    serverSplitter.stop()

    super.postStop()
  }

  override def receive: Receive = {
    case MemberUp(member) =>
      println(s"### MEMBERUP $member ${isLocal(member.address)}")
      if (isLocal(member.address)) {
        serverSplitter.go()
      }

//    case SendBirth =>
//      remoteServers.foreach(server => system.actorSelection(buildPath(server)) ! ServerOnline)

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
      usersActor ! SplitMe
      channelsActor ! SplitMe
  }


  def sendServersOnline(remoteServer: ActorRef) =
    servers
      .filterNot(_ == remoteServer)
      .foreach(remoteServer ! ServerOnline(_))

  def sendServersOffline(remoteServer: ActorRef) =
    servers
      .foreach(remoteServer ! ServerOffline(_))
}
