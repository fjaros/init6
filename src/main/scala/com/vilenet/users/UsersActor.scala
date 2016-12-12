package com.vilenet.users

import java.util.concurrent.TimeUnit

import akka.actor.{Address, Terminated, Props, ActorRef}
import akka.cluster.ClusterEvent.{UnreachableMember, MemberUp}
import akka.pattern.ask
import akka.util.Timeout
import com.vilenet.channels.utils.{RemoteMultiMap, LocalUsersSet}
import com.vilenet.coders.commands._
import com.vilenet.servers._
import com.vilenet.{ViLeNetClusterActor, Constants, ViLeNetComponent}
import com.vilenet.channels._
import com.vilenet.Constants._
import com.vilenet.utils.RealKeyedCaseInsensitiveHashMap
import com.vilenet.utils.FutureCollector._

import scala.collection.mutable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success}

/**
 * Created by filip on 9/28/15.
 */
case class Add(connection: ActorRef, user: User, protocol: Protocol) extends Command
case class Rem(username: String) extends Command
case class RemActors(userActors: Set[ActorRef]) extends Command

case class WhisperTo(user: User, username: String, message: String)  extends Command

object UsersActor extends ViLeNetComponent {
  def apply() = system.actorOf(Props[UsersActor], VILE_NET_USERS_PATH)
}

trait Protocol extends Command
case object BinaryProtocol extends Protocol
case object TelnetProtocol extends Protocol
case object Chat1Protocol extends Protocol

case object GetUsers extends Command
case object GetUptime extends Command
case class ReceivedUptime(actor: ActorRef, uptime: Long) extends Command
case class ReceivedUser(user: (String, ActorRef)) extends Command
case class ReceivedUsers(users: Seq[(String, ActorRef)]) extends Command
case class UserToChannelCommandAck(userActor: ActorRef, realUsername: String, command: UserToChannelCommand) extends Command
case class UsersUserAdded(userActor: ActorRef, user: User) extends Command

class UsersActor extends ViLeNetClusterActor {

  TopCommandActor()

  var placeCounter = 1

  val remoteUsersActor = (address: Address) =>
    system.actorSelection(s"akka://${address.hostPort}/user/$VILE_NET_USERS_PATH")

  val users = RealKeyedCaseInsensitiveHashMap[ActorRef]()
  val reverseUsers = mutable.HashMap[ActorRef, String]()
  val localUsers = LocalUsersSet()
  val remoteUsersMap = RemoteMultiMap[Address, ActorRef]()

  subscribe(TOPIC_ONLINE)
  subscribe(TOPIC_USERS)
  subscribe(TOPIC_SPLIT)

  private def sendGetUsers(address: Address): Unit = {
    remoteUsersActor(address).resolveOne(Timeout(5, TimeUnit.SECONDS).duration).onComplete {
      case Success(actor) =>
        actor ! GetUsers

      case Failure(ex) =>
        system.scheduler.scheduleOnce(Timeout(500, TimeUnit.MILLISECONDS).duration, new Runnable {
          override def run(): Unit = sendGetUsers(address)
        })
    }
  }

  override def receive: Receive = {
    case MemberUp(member) =>
      if (!isLocal(member.address)) {
        sendGetUsers(member.address)
      }

    case UnreachableMember(member) =>
      remoteUsersMap.get(member.address).foreach(unreachableActors => {
        unreachableActors.foreach(unreachableActor => {
          reverseUsers.get(unreachableActor).foreach(username => {
            rem(unreachableActor, username)
          })
        })
      })

    case ServerOnline =>
      publish(TOPIC_USERS, GetUsers)

    case GetUsers =>
      sender() ! ReceivedUsers(users.values.toSeq)

    case ReceivedUsers(remoteUsers) =>
      if (!isLocal()) {
        val address = sender().path.address
        remoteUsers
          .map(_._2)
          .foreach(context.watch)
        remoteUsersMap ++= address -> remoteUsers.map(_._2)

        // yeah .. but each server gets this...
        implicit val timeout = Timeout(10, TimeUnit.SECONDS)
        remoteUsers.foreach {
          case (name, actor) =>
            users.get(name).fold[Unit]({
              users += name -> actor
              reverseUsers += actor -> name
            }) {
              case (currentName, currentActor) =>
                println(actor)
                println(currentActor)
                if (actor != currentActor) {
                  println(actor)
                  println(currentActor)
                  val remoteActorUptime = actor ? GetUptime
                  val localActorUptime = currentActor ? GetUptime

                  Seq(remoteActorUptime, localActorUptime).collectResults {
                    case ReceivedUptime(actor, uptime) =>
                      println(uptime + " - " + sender())
                      Some(actor -> uptime)
                  }.foreach(uptimeSeq => {
                    val (toKill, toKeep) =
                      if (uptimeSeq.head._2 > uptimeSeq.last._2) {
                        (uptimeSeq.head._1, uptimeSeq.last._1)
                      } else {
                        (uptimeSeq.last._1, uptimeSeq.head._1)
                      }

                    toKill ! KillSelf
                    // This is a race condition
                    rem(toKill)

                    users += name -> toKeep
                    reverseUsers += toKeep -> name
                  })
                }
            }
        }
      }

    case command: UserToChannelCommand =>
      sender() ! users.get(command.toUsername)
        .fold[Command](UserError(Constants.USER_NOT_LOGGED_ON)) {
          case (name, actor) => UserToChannelCommandAck(actor, name, command)
      }

    case command: UserCommand =>
      //log.error(s"command sending $command")
      users.get(command.toUsername)
        .fold(sender() ! UserError(Constants.USER_NOT_LOGGED_ON)) {
          case (_, actor) =>
            ////log.error(s"users $users")
            //log.error(s"sending to $x from ${sender()}")
            actor ! (sender(), command)
          }

    case UsersCommand =>
      println(users)
      sender() ! UserInfo(USERS(localUsers.size, users.size))

    case Terminated(actor) =>
      rem(actor, reverseUsers.getOrElse(actor, ""))

    case RemoteEvent(event) =>
      handleRemote(event)

    case event =>
      //log.error(s"event $event from ${sender()}")
      handleLocal(event)
  }

  def handleLocal: Receive = {
    case SplitMe =>
      if (isLocal()) {
        publish(TOPIC_USERS, RemoteEvent(RemActors(localUsers.toSet)))
        users
          .filterNot {
            case (key, (name, actor)) =>
              localUsers.contains(actor)
          }
          .foreach {
            case (key, (name, actor)) =>
              reverseUsers.get(actor).foreach(username => {
                self ! Rem(username)
              })
          }
      }

    case c @ Add(connection, user, protocol) =>
      println("#ADD " + c)
      val newUser = getRealUser(user).copy(place = placeCounter)
      placeCounter += 1
      val userActor = context.actorOf(UserActor(connection, newUser, protocol))
      users += newUser.name -> userActor
      reverseUsers += userActor -> newUser.name
      localUsers += userActor
      publish(TOPIC_USERS, RemoteEvent(Add(userActor, newUser, protocol)))
      sender() ! UsersUserAdded(userActor, newUser)

    case c @ Rem(username) =>
      println("#REM " + c)
      rem(sender(), username)

    case BroadcastCommand(message) =>
      publish(TOPIC_USERS, RemoteEvent(BroadcastCommand(message)))

    case _ =>
  }

  def rem(userActor: ActorRef) = {
    localUsers -= userActor
    reverseUsers.get(userActor).foreach(username => {
      reverseUsers -= userActor
      users -= username
    })
  }

  def rem(userActor: ActorRef, username: String) = {
    localUsers -= userActor
    reverseUsers -= userActor
    users -= username
  }

  def handleRemote: Receive = {
    case Add(userActor, user, _) =>
      if (!isLocal()) {
        users += user.name -> userActor
        reverseUsers += userActor -> user.name
      }

    case c @ RemActors(userActors) =>
      if (!isLocal()) {
        //println(c)
        userActors.foreach(userActor => {
          reverseUsers.get(userActor).foreach(username => {
            users -= username
            reverseUsers -= userActor
          })
        })
      }

    case BroadcastCommand(message) =>
      //println(s"### Remote Broadcast $localUsers")
      localUsers ! UserError(message)
  }

  def getRealUser(user: User): User = {
    var number = 1
    var username = user.name
    while (users.contains(username)) {
      number = number + 1
      username = s"${user.name}#$number"
    }
    user.copy(name = username)
  }
}
