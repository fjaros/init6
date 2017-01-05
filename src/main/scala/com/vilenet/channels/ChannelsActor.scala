package com.vilenet.channels

import java.util.concurrent.TimeUnit

import akka.actor.{ActorRef, Address, PoisonPill, Props}
import akka.pattern.ask
import akka.util.Timeout
import com.vilenet.Constants._
import com.vilenet.coders.Base64
import com.vilenet.{ViLeNetComponent, ViLeNetRemotingActor}
import com.vilenet.coders.commands._
import com.vilenet.servers._
import com.vilenet.utils.FutureCollector.futureSeqToFutureCollector
import com.vilenet.utils.RealKeyedCaseInsensitiveHashMap

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Await
import scala.util.{Failure, Success, Try}


/**
 * Created by filip on 9/20/15.
 */
object ChannelsActor extends ViLeNetComponent {
  def apply() = system.actorOf(Props[ChannelsActor].withDispatcher(CHANNELS_DISPATCHER), VILE_NET_CHANNELS_PATH)
}

case object GetChannels extends Command
case class ChannelsAre(channels: Seq[(String, ActorRef)]) extends Command
case class ChannelCreated(actor: ActorRef, name: String) extends Command
case object GetChannelUsers extends Command
case class ReceivedChannelUsers(users: Seq[(ActorRef, User)]) extends Command
case class ReceivedChannel(channel: (String, ActorRef)) extends Command
case class UserAdded(actor: ActorRef, channel: String) extends Command
case object ChannelEmpty extends Command
case object ChannelNotEmpty extends Command
case class ChannelDeleted(name: String) extends Command
case object MrCleanChannelEraser extends Command
case class KillChannel(actor: ActorRef, channel: String) extends Command with Remotable

class ChannelsActor extends ViLeNetRemotingActor {

  override val actorPath = VILE_NET_CHANNELS_PATH
  implicit val timeout = Timeout(1000, TimeUnit.MILLISECONDS)

  val remoteChannelsActor = (address: Address) =>
    system.actorSelection(s"akka://${address.hostPort}/user/$VILE_NET_CHANNELS_PATH")

  val channels = RealKeyedCaseInsensitiveHashMap[ActorRef]()

//  system.scheduler.schedule(
//    Timeout(1, TimeUnit.SECONDS).duration, Timeout(1, TimeUnit.SECONDS).duration, self, MrCleanChannelEraser
//  )

  private def sendGetChannels(address: Address): Unit = {
    remoteChannelsActor(address).resolveOne(Timeout(5, TimeUnit.SECONDS).duration).onComplete {
      case Success(actor) =>
        actor ! GetChannels

      case Failure(ex) =>
        system.scheduler.scheduleOnce(Timeout(500, TimeUnit.MILLISECONDS).duration, new Runnable {
          override def run(): Unit = sendGetChannels(address)
        })
    }
  }


  override protected def onServerAlive(address: Address) = {
    sendGetChannels(address)
  }

  override def receive: Receive = {
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
              self ! KillChannel(actor, name)
            }
            None
        }, Timeout(1, TimeUnit.SECONDS).duration)
      }.getOrElse(log.error("Failed to clean channels due to timeout."))

    case KillChannel(actor, name) =>
      actor ? PoisonPill
      channels -= name

    case SplitMe =>
      if (isRemote()) {

        //remoteChannelsActors -= sender().path.address
      }

    case ServerOnline =>
      //publish(TOPIC_CHANNELS, GetChannels)

    case c@ GetChannels =>
      log.error(s"### $c $channels")
      if (isRemote()) {
        val remoteActor = sender()
        remoteActor ! ChannelsAre(channels.values.toSeq)
      }

    case c@ ChannelsAre(remoteChannels) =>
      log.error(s"### $c")
      remoteChannels
        .foreach {
          case (name, actor) => getOrCreate(name)
        }

    case c@ ChannelCreated(actor, name) =>
      if (isRemote()) {
        getOrCreate(name)
      }

    case command @ UserLeftChat(user) =>

    case command @ UserSwitchedChat(actor, user, channel) =>

      val userActor = sender()
      Try {
        Await.result(getOrCreate(channel) ? AddUser(actor, user), timeout.duration) match {
          case reply: UserAddedToChannel =>
            if (!user.inChannel.equalsIgnoreCase(channel)) {
              channels.get(user.inChannel).foreach {
                case (_, oldChannelActor) =>
                  oldChannelActor.tell(RemUser(actor), self)
              }
            }
            // temp actor
            if (isLocal(userActor)) {
              userActor ! ChannelJoinResponse(UserChannel(reply.user, reply.channelName, reply.channelActor))
              // real actor
              if (reply.channelTopic.nonEmpty) {
                userActor ! UserInfo(CHANNEL_TOPIC(reply.channelTopic))
              }
            }
          case reply: ChatEvent =>
            if (isLocal(userActor)) {
              userActor ! ChannelJoinResponse(reply)
            }
          case msg =>
            println(msg)
        }
      }.getOrElse({
          log.error("ChannelsActor createOrGet timed out for {}", command)
          if (isLocal(userActor)) {
            userActor ! ChannelJoinResponse(UserError(CHANNEL_FAILED_TO_JOIN(channel)))
          }
      })

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

    case c @ RemUser(actor) =>
      channels.values.map(_._2).foreach(_ ! c)
  }

  def getChannel(name: String): Option[ActorRef] = {
    channels.get(name).fold[Option[ActorRef]](None) {
      case (_, actor) => Some(actor)
    }
  }

  def getOrCreate(name: String) = {
    val channelActor = channels.getOrElse(name, {
      val channelActor = context.actorOf(ChannelActor(name).withDispatcher(CHANNEL_DISPATCHER), Base64(name.toLowerCase))
      channels += name -> channelActor
      name -> channelActor
    })._2

    channelActor
  }
}
