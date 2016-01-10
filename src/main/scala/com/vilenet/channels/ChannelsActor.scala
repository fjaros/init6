package com.vilenet.channels

import java.util.concurrent.TimeUnit

import akka.actor.{Address, ActorRef, Props}
import akka.cluster.ClusterEvent.{UnreachableMember, MemberUp}
import akka.util.Timeout
import com.vilenet.Constants._
import com.vilenet.coders.commands.{WhoCommandToChannel, WhoCommand, ChannelsCommand, Command}
import com.vilenet.{ViLeNetClusterActor, ViLeNetComponent}
import com.vilenet.servers._
import com.vilenet.utils.RealKeyedCaseInsensitiveHashMap

import scala.collection.mutable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success}


/**
 * Created by filip on 9/20/15.
 */
object ChannelsActor extends ViLeNetComponent {
  def apply() = system.actorOf(Props[ChannelsActor], VILE_NET_CHANNELS_PATH)
}

case object GetChannels extends Command
case object CleanChannels extends Command
case class ChannelsAre(channels: RealKeyedCaseInsensitiveHashMap[ActorRef]) extends Command
case class ChannelCreated(actor: ActorRef, name: String) extends Command
case class GetChannelUsers(remoteActor: ActorRef) extends Command
case class ReceivedChannel(channel: (String, ActorRef)) extends Command
case class UserAdded(actor: ActorRef, channel: String) extends Command


class ChannelsActor extends ViLeNetClusterActor {

  val remoteChannelsActor = (address: Address) =>
    system.actorSelection(s"akka.tcp://${address.hostPort}/user/$VILE_NET_CHANNELS_PATH")

  val remoteChannelsActors = mutable.HashMap[Address, ActorRef]()

  val channels = RealKeyedCaseInsensitiveHashMap[ActorRef]()

  subscribe(TOPIC_ONLINE)
  subscribe(TOPIC_CHANNELS)
  subscribe(TOPIC_SPLIT)

//  system.scheduler.schedule(
//    Timeout(5, TimeUnit.SECONDS).duration, Timeout(5, TimeUnit.SECONDS).duration, self, CleanChannels
//  )

  private def sendGetChannels(address: Address): Unit = {
    remoteChannelsActor(address).resolveOne(Timeout(5, TimeUnit.SECONDS).duration).onComplete {
      case Success(actor) =>
        remoteChannelsActors += address -> actor
        actor ! GetChannels

      case Failure(ex) =>
        system.scheduler.scheduleOnce(Timeout(500, TimeUnit.MILLISECONDS).duration, new Runnable {
          override def run(): Unit = sendGetChannels(address)
        })
    }
  }


  override def receive: Receive = {
    case MemberUp(member) =>
      if (!isLocal(member.address)) {
        sendGetChannels(member.address)
      }

    case UnreachableMember(member) =>
      remoteChannelsActors -= member.address

    case CleanChannels =>

    case SplitMe =>
      if (!isLocal()) {
        remoteChannelsActors -= sender().path.address
      }

    case ServerOnline =>
      publish(TOPIC_CHANNELS, GetChannels)

    case c@ GetChannels =>
      //log.error(s"### $c $channels")
      if (!isLocal()) {
        val remoteChannelActor = sender()
        remoteChannelsActors += remoteChannelActor.path.address -> remoteChannelActor
        remoteChannelActor ! ChannelsAre(channels) // (remoteChannelActor == remote CHANNELS actor)
      }

    case c@ ChannelsAre(remoteChannels) =>
      //log.error(s"### $c")
      remoteChannels
        .values
        .foreach {
          case (name, actor) => getOrCreate(name, Some(actor))
        }

    case c@ ChannelCreated(actor, name) =>
      //log.error(s"### CHANNEL CREATED $c")
      //log.error(s"ChannelCreated $actor $name")
      if (!isLocal() &&
        name != "The Void" // haaackk:'(
      ) {
        getOrCreate(name) ! ChannelCreated(actor, name)
      }

    case UserSwitchedChat(actor, user, channel) =>
      //log.error(s"$user switched chat")

      getOrCreate(channel) ! RemUser(actor)
      getOrCreate(channel) ! AddUser(actor, user)

    case ChannelsCommand =>
      sender() ! UserInfo(CHANNEL_LIST(channels.size))
      channels
        .values
        .foreach {
          case (_, actor) => actor ! ChannelsCommand(sender())
        }

    case WhoCommand(user, channel) =>
      getChannel(channel).fold(sender() ! UserErrorArray(CHANNEL_NOT_EXIST))(_ ! WhoCommandToChannel(sender(), user))
  }

  def getChannel(name: String): Option[ActorRef] = {
    channels.get(name).fold[Option[ActorRef]](None) {
      case (_, actor) => Some(actor)
    }
  }
  
  def getOrCreate(name: String, remoteActor: Option[ActorRef] = None) = {
    val channelActor = channels.getOrElse(name, {
      val channelActor = context.actorOf(ChannelActor(name, remoteActor).withDispatcher(CHANNEL_DISPATCHER))
      channels += name -> channelActor
      //log.error(s"### getOrCreate Publishing ${ChannelCreated(channelActor, name)}")
      remoteChannelsActors.values.foreach(_ ! ChannelCreated(channelActor, name))
      name -> channelActor
    })._2

    remoteActor.foreach(remoteActor => {
      channelActor ! ChannelCreated(remoteActor, name)
    })

    channelActor
  }
}
