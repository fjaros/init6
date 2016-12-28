package com.vilenet.channels

import java.util.concurrent.TimeUnit

import akka.actor.{PoisonPill, Address, ActorRef, Props}
import akka.cluster.ClusterEvent.{UnreachableMember, MemberUp}
import akka.pattern.ask
import akka.util.Timeout
import com.vilenet.Constants._
import com.vilenet.coders.commands._
import com.vilenet.{ViLeNetClusterActor, ViLeNetComponent}
import com.vilenet.servers._
import com.vilenet.utils.FutureCollector.futureSeqToFutureCollector
import com.vilenet.utils.RealKeyedCaseInsensitiveHashMap

import scala.collection.mutable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Await
import scala.util.{Try, Failure, Success}


/**
 * Created by filip on 9/20/15.
 */
object ChannelsActor extends ViLeNetComponent {
  def apply() = system.actorOf(Props[ChannelsActor].withDispatcher(CHANNELS_DISPATCHER), VILE_NET_CHANNELS_PATH)
}

case object GetChannels extends Command
case class ChannelsAre(channels: Seq[(String, ActorRef)]) extends Command
case class ChannelCreated(actor: ActorRef, name: String) extends Command
case class GetChannelUsers(remoteActor: ActorRef) extends Command
case class ReceivedChannel(channel: (String, ActorRef)) extends Command
case class UserAdded(actor: ActorRef, channel: String) extends Command
case object ChannelEmpty extends Command
case object ChannelNotEmpty extends Command
case class ChannelDeleted(name: String) extends Command
case object MrCleanChannelEraser extends Command


class ChannelsActor extends ViLeNetClusterActor {

  implicit val timeout = Timeout(100, TimeUnit.MILLISECONDS)

  val remoteChannelsActor = (address: Address) =>
    system.actorSelection(s"akka://${address.hostPort}/user/$VILE_NET_CHANNELS_PATH")

  val remoteChannelsActors = mutable.HashMap[Address, ActorRef]()

  val channels = RealKeyedCaseInsensitiveHashMap[ActorRef]()

  subscribe(TOPIC_ONLINE)
  subscribe(TOPIC_CHANNELS)
  subscribe(TOPIC_SPLIT)

  system.scheduler.schedule(
    Timeout(15, TimeUnit.SECONDS).duration, Timeout(15, TimeUnit.SECONDS).duration, self, MrCleanChannelEraser
  )

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

    case MrCleanChannelEraser =>
      val futureSeq = channels
        .values
        .map {
          case (_, actor) =>
            actor ? CheckSize
        }

      Try {
        Await.result(futureSeq.collectResults {
          case ChannelSize(actor, name, size) =>
            if (size == 0) {
              actor ! PoisonPill
              channels -= name
            }
            None
        }, Timeout(1, TimeUnit.SECONDS).duration)
      }.getOrElse(log.error("Failed to clean channels due to timeout."))

    case SplitMe =>
      if (!isLocal()) {
        remoteChannelsActors -= sender().path.address
      }

    case ServerOnline =>
      publish(TOPIC_CHANNELS, GetChannels)

    case c@ GetChannels =>
      log.error(s"### $c $channels")
      if (!isLocal()) {
        val remoteActor = sender()
        remoteChannelsActors += remoteActor.path.address -> remoteActor
        remoteActor ! ChannelsAre(channels.values.toSeq)
      }

    case c@ ChannelsAre(remoteChannels) =>
      log.error(s"### $c")
      remoteChannels
        .foreach {
          case (name, actor) => getOrCreate(name, Some(actor))
        }

    case c@ ChannelCreated(actor, name) =>
      //log.error(s"### $c")
      if (!isLocal()) {
        getOrCreate(name) ! ChannelCreated(actor, name)
      }

    case command @ UserLeftChat(user) =>
      channels.get(user.channel).foreach {
        case (_, oldChannelActor) =>
          oldChannelActor ! RemUser(sender())
      }

    case command @ UserSwitchedChat(actor, user, channel) =>
      log.debug("UserSwitchedChat {}", command)

      val userActor = sender()
      (getOrCreate(channel) ? AddUser(actor, user)).onComplete {
        case Success(reply) =>
          reply match {
            case reply: UserAddedToChannel =>
              channels.get(user.channel).foreach {
                case (_, oldChannelActor) =>
                  oldChannelActor ! RemUser(actor)
              }
              // temp actor
              userActor ! UserChannel(reply.user, reply.channelName, reply.channelActor)
              // real actor
              if (reply.channelTopic.nonEmpty) {
                actor ! UserInfo(CHANNEL_TOPIC(reply.channelTopic))
              }
            case x =>
              log.error("{} Invalid getOrCreate return {}", command, x)
              userActor ! reply
          }
        case reply => userActor ! reply
      }

    case ChannelsCommand =>
      val replyActor = sender()

      channels
        .values
        .map {
          case (_, actor) => actor ? ChannelsCommand
        }
        .collectResults {
          case ChannelInfo(name, size, topic) if size > 0 => Some(name -> (size, topic))
          case _ => None
        }
        .foreach(responses => {
          if (responses.nonEmpty) {
            val sortedResponses = responses.sortBy(_._2._1)(Ordering[Int].reverse)

            replyActor ! UserInfo(CHANNEL_LIST(sortedResponses.size))
            sortedResponses.foreach {
              case (name, (size, topic)) => replyActor ! UserInfo(CHANNEL_INFO(name, size, topic))
            }
          } else {
            replyActor ! UserInfo(CHANNEL_LIST_EMPTY)
          }
        })

    case WhoCommand(user, channel) =>
      getChannel(channel).fold(sender() ! UserErrorArray(CHANNEL_NOT_EXIST))(actor => {
        actor ! WhoCommandToChannel(sender(), user)
      })
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
