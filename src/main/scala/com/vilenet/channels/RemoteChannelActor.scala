package com.vilenet.channels

import akka.actor.{Address, Terminated, ActorRef}
import akka.cluster.ClusterEvent.{UnreachableMember, MemberRemoved}
import akka.cluster.pubsub.DistributedPubSubMediator.{Subscribe, SubscribeAck}
import com.vilenet.Constants._
import com.vilenet.ViLeNetClusterActor
import com.vilenet.channels.utils.RemoteChannelsMultiMap
import com.vilenet.coders.commands.{Command, EmoteCommand, ChatCommand}
import com.vilenet.servers.{ServerOnline, SplitMe, RemoteEvent}

import scala.collection.mutable

case class RemAll(remoteUsers: mutable.HashSet[ActorRef]) extends Command

/**
  * Created by filip on 11/14/15.
  */
trait RemoteChannelActor extends ChannelActor with ViLeNetClusterActor {

  // Map of Actor -> Set[User], actor key is remote server's copy of this channel.
  var remoteUsers = RemoteChannelsMultiMap()

  var remoteServers = mutable.HashMap[Address, ActorRef]()

  subscribe(TOPIC_CHANNEL)
  subscribe(TOPIC_SPLIT)

  override def receiveEvent: Receive = {
    case UnreachableMember(member) =>
      remoteServers.get(member.address).fold()(removedActor => {
        remoteUsers(removedActor).foreach(remoteRem)
        remoteUsers -= removedActor
      })
      remoteServers -= member.address

    case SubscribeAck(Subscribe(topic, _, actor)) if topic == TOPIC_CHANNEL =>
      //log.error(s"### SUBSCRIBEACK $TOPIC_CHANNEL")

    case ServerOnline =>
      subscribe(TOPIC_CHANNEL)

    case SplitMe =>
      //println(s"### SplitMe ${isLocal()}")
      if (isLocal()) {
        unsubscribe(TOPIC_CHANNEL)
        remoteUsers
          .values
          .foreach(_.foreach(remoteRem))
        remoteUsers ! RemAll(localUsers)
        remoteUsers.clear()
      }

    case ChannelUsersResponse(n,u,ru) =>
      self ! RemoteEvent(ChannelUsersLoad(sender(), u, ru))

    case ChannelCreated(remoteChannelActor, _) =>
      //println(s"ChannelCreated $remoteChannelActor")
      context.watch(remoteChannelActor)
      remoteUsers += remoteChannelActor
      remoteServers += remoteChannelActor.path.address -> remoteChannelActor
      remoteChannelActor ! RemoteEvent(ChannelUsersLoad(self, users, localUsers))


    case ChannelUsersRequest(remoteChannelsActor) =>
      //println(s"ChannelUsersRequest $remoteChannelsActor")
      remoteChannelsActor ! ChannelUsersResponse(name, users, localUsers)


    case GetChannels =>
      //log.error(s"### ChannelUsersRequest ${sender()} ${isLocal()}")
      if (!isLocal()) {
        val remoteChannelsActor = sender()
        remoteChannelsActor ! ChannelUsersResponse(name, users, localUsers)
      }

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
    case ChannelUsersLoad(remoteChannelActor, allUsers, remoteUsersLoad) =>
      //log.error(s"ChannelUsersLoad $remoteChannelActor $allUsers $remoteUsersLoad")
      if (allUsers.nonEmpty) {
        onChannelUsersLoad(allUsers, remoteUsersLoad)
      }
      if (remoteUsersLoad.nonEmpty) {
        remoteUsersLoad.foreach(context.watch)
      }
      remoteUsers += remoteChannelActor -> remoteUsersLoad

    case AddUser(actor, user) => remoteAdd(actor, user)
    case RemUser(actor) => remoteRem(actor)
    case RemAll(userActors) =>
      remoteUsers -= sender()
      userActors.foreach(remoteRem)

    case ChatCommand(user, message) =>
      //println("Remote chat msg")
      localUsers ! UserTalked(user, message)
    case EmoteCommand(user, message) =>
      localUsers ! UserEmote(user, message)
    case event =>
  }

  def onChannelUsersLoad(allUsers: mutable.Map[ActorRef, User], remoteUsersLoad: mutable.Set[ActorRef]) = {
    allUsers
      .filterNot { case (actor, _) => users.contains(actor) }
      .foreach(tuple => {
        //log.error(s"Adding User From Load $tuple")
        users += tuple
        localUsers ! UserIn(tuple._2)
      })
  }

  override def add(actor: ActorRef, user: User): User = {
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
    //println(s"REMOTEADD $name ${user.name}")
    context.watch(actor)
    users += actor -> user
  }

  def remoteRem(actor: ActorRef): Option[User] = {
    val userOpt = users.get(actor)
    userOpt.fold()(_ => {
      context.unwatch(actor)
      users -= actor
    })

    if (users.isEmpty) {
      channelsActor ! ChatEmptied(name)
    }
    userOpt
  }
}
