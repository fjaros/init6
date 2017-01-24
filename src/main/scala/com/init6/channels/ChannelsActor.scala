package com.init6.channels

import java.util.concurrent.TimeUnit

import akka.actor.{ActorRef, Address, PoisonPill, Props}
import akka.pattern.ask
import akka.util.Timeout
import com.init6.Constants._
import com.init6.coders.Base64
import com.init6.{Init6Component, Init6RemotingActor}
import com.init6.coders.commands._
import com.init6.servers._
import com.init6.utils.FutureCollector.futureSeqToFutureCollector
import com.init6.utils.RealKeyedCaseInsensitiveHashMap

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Await
import scala.util.{Failure, Success, Try}


/**
 * Created by filip on 9/20/15.
 */
object ChannelsActor extends Init6Component {
  def apply() = system.actorOf(Props[ChannelsActor].withDispatcher(CHANNELS_DISPATCHER), INIT6_CHANNELS_PATH)
}

case object GetChannels extends Command
case class ChannelsAre(channels: Seq[(String, ActorRef)]) extends Command
case object GetChannelUsers extends Command
case class ReceivedChannelUsers(users: Seq[(ActorRef, User)], topic: TopicExchange) extends Command
case class ReceivedBannedUsers(names: Seq[(ActorRef, Seq[String])]) extends Command
case class ReceivedDesignatedActors(designatedActors: Seq[(ActorRef, ActorRef)]) extends Command
case class ReceivedChannel(channel: (String, ActorRef)) extends Command
case class UserAdded(actor: ActorRef, channel: String) extends Command
case object ChannelEmpty extends Command
case object ChannelNotEmpty extends Command
case class ChannelDeleted(name: String) extends Command
case object MrCleanChannelEraser extends Command
case class KillChannel(actor: ActorRef, channel: String) extends Command with Remotable

class ChannelsActor extends Init6RemotingActor {

  override val actorPath = INIT6_CHANNELS_PATH
  implicit val timeout = Timeout(1000, TimeUnit.MILLISECONDS)

  val channels = RealKeyedCaseInsensitiveHashMap[ActorRef]()

//  system.scheduler.schedule(
//    Timeout(1, TimeUnit.SECONDS).duration, Timeout(1, TimeUnit.SECONDS).duration, self, MrCleanChannelEraser
//  )

  private def sendGetChannels(address: Address): Unit = {
    remoteActorSelection(address).resolveOne(Timeout(2, TimeUnit.SECONDS).duration).onComplete {
      case Success(actor) =>
        actor ! GetChannels

      case Failure(ex) =>
        system.scheduler.scheduleOnce(Timeout(500, TimeUnit.MILLISECONDS).duration)(sendGetChannels(address))
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
        if (!remoteActors.contains(remoteActorSelection(remoteActor.path.address))) {
          remoteActor ! GetChannels
          remoteActors += remoteActorSelection(remoteActor.path.address)
        }
      }

    case c@ ChannelsAre(remoteChannels) =>
      log.error(s"### $c")
      remoteChannels
        .foreach {
          case (name, actor) => getOrCreate(name)
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
              if (reply.topicExchange.topic.nonEmpty) {
                userActor ! UserInfo(CHANNEL_TOPIC(reply.topicExchange.topic))
              }
              // special case for now - refactor later
              // !!!!!!!!!!!!!!!
              // NEED TO GIVE REMOTES THE CORRECT FLAGS FROM THIS SERVER!!!!
              remoteActors.foreach(_.tell(UserSwitchedChat(actor, reply.user.copy(inChannel = user.inChannel), channel), userActor))
            }
          case reply: ChatEvent =>
            if (isLocal(userActor)) {
              userActor ! ChannelJoinResponse(reply)
            }
          case msg =>
            println(msg)
        }
      }.getOrElse({
          log.error("ChannelsActor getOrCreate timed out for {}", command)
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
          case channelInfo @ ChannelInfo(name, size, topic, creationTime) if size > 0 => Some(channelInfo)
          case _ => None
        }
        .foreach(responses => {
          if (responses.nonEmpty) {
            val sortedResponses = responses.sortWith((c1, c2) => {
              if (c1.creationTime == 0) {
                false
              } else if (c2.creationTime == 0) {
                c1.creationTime > 0
              } else {
                c2.creationTime > c1.creationTime
              }
            })

            replyActor ! UserInfo(CHANNEL_LIST(sortedResponses.size))
            sortedResponses.foreach {
              case ChannelInfo(name, size, topic, creationTime) => replyActor ! UserInfo(CHANNEL_INFO(name, size, topic, creationTime))
            }
          } else {
            replyActor ! UserInfo(CHANNEL_LIST_EMPTY)
          }
        })

    case WhoCommand(user, channel, opsOnly) =>
      getChannel(channel).fold(sender() ! UserErrorArray(CHANNEL_NOT_EXIST))(actor => {
        actor ! WhoCommandToChannel(sender(), user, opsOnly)
      })

    // Need to get rid of this in the future. Puts too much strain on the outbound queue
    case c @ RemUser(actor) =>
      //println("##RemUser " + c + " - " + sender() + " - " + remoteActors)
      channels.values.map(_._2).foreach(_ ! c)
  }

  def getChannel(name: String): Option[ActorRef] = {
    channels.get(name).fold[Option[ActorRef]](None) {
      case (_, actor) => Some(actor)
    }
  }

  def getOrCreate(name: String) = {
    val fixedName = nameFixer(name)

    val channelActor = channels.getOrElse(fixedName, {
      val channelActor = context.actorOf(ChannelActor(fixedName).withDispatcher(CHANNEL_DISPATCHER), Base64(fixedName.toLowerCase))
      channels += fixedName -> channelActor
      fixedName -> channelActor
    })._2

    channelActor
  }

  def nameFixer(name: String) = {
    name.toLowerCase match {
      case "init 6" | "init6" => "init 6"
      case _ => name
    }
  }
}
