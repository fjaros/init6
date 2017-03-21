package com.init6.channels

import akka.actor.{ActorRef, Address}
import com.init6.Config
import com.init6.Constants._
import com.init6.coders.commands.DesignateCommand
import com.init6.users.{UserToChannelCommandAck, UserUpdated}

import scala.collection.mutable

/**
  * Created by filip on 11/24/15.
  */
trait OperableChannelActor extends ChannelActor {

  val designatedActors = mutable.HashMap[ActorRef, ActorRef]()

  override def receiveEvent = ({
    case command @ GetChannelUsers =>
      sender() ! ReceivedDesignatedActors(designatedActors.toSeq)
      super.receiveEvent(command)

    case ReceivedDesignatedActors(designatedActors) =>
      this.designatedActors ++= designatedActors

    case command: UserToChannelCommandAck =>
      val userActor = sender()
      command.command match {
        case DesignateCommand(_, _) =>
          designate(userActor, command.userActor)
        case _ =>
      }
      super.receiveEvent(command)
  }: Receive)
    .orElse(super.receiveEvent)

  override def add(actor: ActorRef, user: User): User = {
    val newUser =
      if (shouldReceiveOps(actor, user)) {
        Flags.op(user)
      } else {
        user
      }

    super.add(actor, newUser)
  }

  override def rem(actor: ActorRef): Option[User] = {
    val userOpt = super.rem(actor)

    if (!Config().Server.Chat.enabled) {
      userOpt.foreach(user => {
        if (users.nonEmpty && !existsOperator()) {
          val designatedActorOpt = designatedActors.get(actor)
          val (oppedActor, oppedUser) =
            if (designatedActorOpt.isDefined && users.contains(designatedActorOpt.get)) {
              // designated is in the channel
              designatedActorOpt.get -> Flags.op(users(designatedActorOpt.get))
            } else {
              val possibleNextOpActor = determineNextOp
              possibleNextOpActor -> Flags.op(users(possibleNextOpActor))
            }
          log.info("###OPPED " + oppedActor + " - " + oppedUser)

          users += oppedActor -> oppedUser
          designatedActors -= actor
          oppedActor ! UserUpdated(oppedUser)
          localUsers ! UserFlags(oppedUser)

          remoteActors.foreach(_ ! InternalChannelUserUpdate(oppedActor, oppedUser))
        }
      })
    }

    userOpt
  }

  def designate(actor: ActorRef, designatee: ActorRef) = {
    users.get(actor).foreach(user => {
      val result =
        if (Flags.isOp(user)) {
          users.get(designatee).fold[ChatEvent](UserError(INVALID_USER))(designatedUser => {
            designatedActors += actor -> designatee
            UserInfo(USER_DESIGNATED(designatedUser.name))
          })
        } else {
          UserError(NOT_OPERATOR)
        }
      if (isLocal(actor)) {
        actor ! result
      }
    })
  }

  // In case of /rejoin as only channel member
  def shouldReceiveOps(actor: ActorRef, user: User) = {
    Flags.isSpecialOp(user) ||
      (isLocal(actor) && (users.isEmpty || (users.size == 1 && users.head._1 == actor)))
  }

  def existsOperator() = {
    // O(n) sadface
    !users.values.forall(!Flags.isOp(_))
  }

  def determineNextOp: ActorRef = {
    users.min(Ordering.by[(ActorRef, User), Long](_._2.channelTimestamp))._1
  }
}
