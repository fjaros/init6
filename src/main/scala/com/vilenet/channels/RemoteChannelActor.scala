package com.vilenet.channels

import java.util.concurrent.TimeUnit

import akka.actor.{Address, Terminated, ActorRef}
import akka.cluster.ClusterEvent.UnreachableMember
import akka.util.Timeout
import com.vilenet.Constants._
import com.vilenet.ViLeNetClusterActor
import com.vilenet.channels.utils.RemoteChannelsMultiMap
import com.vilenet.coders.commands.{Command, EmoteCommand, ChatCommand}
import com.vilenet.servers.{SplitMe, RemoteEvent}
import com.vilenet.users.UserUpdated

import scala.concurrent.ExecutionContext.Implicits.global
import scala.collection.mutable

case class RemAll(remoteUsers: Set[ActorRef]) extends Command
case class ChannelUsersLoad(remoteChannelActor: ActorRef, allUsers: Map[ActorRef, User], remoteUsers: Set[ActorRef]) extends Command
//case class ChannelUsersLoad(remoteChannelActor: ActorRef, allUsers: Seq[(ActorRef, User)], remoteUsers: Set[ActorRef]) extends Command

/**
  * Created by filip on 11/14/15.
  */
trait RemoteChannelActor extends ChannelActor with ViLeNetClusterActor {

  // Map of Actor -> Set[User], actor key is remote server's copy of this channel.
  val remoteUsers = RemoteChannelsMultiMap()

  val remoteServers = mutable.HashMap[Address, ActorRef]()

  subscribe(TOPIC_SPLIT)

  val duration = Timeout(5, TimeUnit.SECONDS).duration

//  system.scheduler.schedule(duration, duration, self, "Hello")

  override def receiveEvent: Receive = {
//    case "Hello" =>
//      log.error(s"##### CHANNEL STATUS: $name $self")
//      log.error(s"##### users: ")
//      users.foreach(msg => log.error(msg.toString()))
//      log.error(s"##### localUsers")
//      localUsers.foreach(msg => log.error(msg.toString()))
//      log.error(s"##### remoteUsers")
//      remoteUsers.foreach(msg => log.error(msg.toString()))

    case UnreachableMember(member) =>
      remoteServers.get(member.address).fold()(removedActor => {
        remoteUsers.get(removedActor).fold()(actorSet => {
          actorSet.foreach(remoteRem)
          remoteUsers -= removedActor
        })
      })
      remoteServers -= member.address

    case SplitMe =>
      if (isLocal()) {
        log.error(s"### SplitMe $remoteUsers")
        remoteUsers
          .values
          .foreach(_.foreach(remoteRem))
        val shit = RemAll(localUsers.toSet)
        //log.error(s"##### SENDING $shit")
        remoteUsers ! RemAll(localUsers.toSet)
        remoteUsers.clear()
      }

    case c@ ChannelCreated(remoteChannelActor, _) =>
      //log.error(s"##### $c")
      context.watch(remoteChannelActor)
      remoteUsers += remoteChannelActor
      remoteServers += remoteChannelActor.path.address -> remoteChannelActor
      val shit = RemoteEvent(ChannelUsersLoad(self, users.toMap, localUsers.toSet))
      //log.error(s"##### SENDING $shit")
      //remoteChannelActor ! RemoteEvent(ChannelUsersLoad(self, users.toSeq, localUsers.toSet))
      remoteChannelActor ! shit

    case RemoteEvent(event) =>
      //println(s"Received Remote $event ${this.getClass.toString}")
      receiveRemoteEvent(event)

    case event: Terminated =>
      //println(s"REMOTECHANNELACTOR TERMINATED ${event.actor}")
      remoteUsers.get(event.actor).fold(super.receiveEvent(event))(_.foreach(remoteRem))

    case event =>
      super.receiveEvent(event)
      ////println(s"Sending $event to $remoteUsers")
      //remoteUsers ! event
  }

  def receiveRemoteEvent: Receive = {
    case c@ ChannelUsersLoad(remoteChannelActor, allUsers, remoteUsersLoad) =>
      //log.error(s"##### $c - ${sender()}")
      if (allUsers.nonEmpty) {
        onChannelUsersLoad(allUsers, remoteUsersLoad)
      }
//      log.error(s"remoteUsersLoad ${remoteUsersLoad.getClass}")
      if (remoteUsersLoad.nonEmpty) {
        remoteUsersLoad.foreach(context.watch)
      }
      remoteUsers += remoteChannelActor -> mutable.Set(remoteUsersLoad.toSeq: _*)

    case AddUser(actor, user) => remoteAdd(actor, user)
    case RemUser(actor) => remoteRem(actor)
    case UserUpdated(user) =>
      super.sendUserUpdate(user)

    case c@ RemAll(userActors) =>
      //log.error(s"##### $c")
      remoteUsers -= sender()
      userActors.foreach(remoteRem)

    case ChatCommand(user, message) =>
      //println("Remote chat msg")
      localUsers ! UserTalked(user, message)
    case EmoteCommand(user, message) =>
      localUsers ! UserEmote(user, message)
    case event =>
  }

  def onChannelUsersLoad(allUsers: Map[ActorRef, User], remoteUsersLoad: Set[ActorRef]) = {
    allUsers
      .filterNot { case (actor, _) => users.contains(actor) }
      .foreach(tuple => {
        //log.error(s"Adding User From Load $tuple")
        users += tuple
        localUsers ! UserIn(tuple._2)
      })
  }

  override def add(actor: ActorRef, user: User): User = {
    //log.error(s"### add $actor $user")
    val finalUser = super.add(actor, user)
    //println(s"remote localAdd $remoteUsers")
    remoteUsers ! AddUser(actor, finalUser)
    finalUser
  }

  override def rem(actor: ActorRef): Option[User] = {
    val finalUserOpt = super.rem(actor)
    //println(s"rem  $remoteUsers")
    remoteUsers ! RemUser(actor)
    finalUserOpt
  }

  def remoteAdd(actor: ActorRef, user: User): Unit = {
    //log.error(s"### remoteAdd $actor $user")
    //println(s"REMOTEADD $name ${user.name}")
    context.watch(actor)
    users += actor -> user
    remoteUsers += sender() -> actor // ?
  }

  def remoteRem(actor: ActorRef): Option[User] = {
    val userOpt = users.get(actor)
    userOpt.fold()(_ => {
      context.unwatch(actor)
      users -= actor
    })

    userOpt
  }

  override def sendUserUpdate(user: User) = {
    remoteUsers ! UserUpdated(user)
    super.sendUserUpdate(user)
  }
}
