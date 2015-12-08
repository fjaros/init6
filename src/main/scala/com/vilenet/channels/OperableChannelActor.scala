package com.vilenet.channels

import akka.actor.ActorRef
import com.vilenet.Constants._
import com.vilenet.coders.{OperableCommand, DesignateCommand}
import com.vilenet.users.{UserToChannelCommandAck, UserUpdated}

/**
  * Created by filip on 11/24/15.
  */
trait OperableChannelActor extends RemoteOperableChannelActor {

  override def receiveEvent = ({
    case command: UserToChannelCommandAck =>
    users.get(sender()).fold()(user => {
      if (Flags.canBan(user)) {
        command.command match {
          case DesignateCommand(_, designatee) =>
            designate(sender(), command.userActor)
          case _ =>
        }
      } else {
        command.command match {
          case command: OperableCommand => sender() ! UserError(NOT_OPERATOR)
          case _ => super.receiveEvent(command)
        }
      }
      super.receiveEvent(command)
    })
  }: Receive)
    .orElse(super.receiveEvent)

  override def add(actor: ActorRef, user: User): User = {
    val newUser =
      if (users.isEmpty) {
        Flags.op(user)
      } else {
        user
      }

    super.add(actor, newUser)
  }

  override def rem(actor: ActorRef): Option[User] = {
    val userOpt = super.rem(actor)

    userOpt.fold()(user => {
      if (users.nonEmpty && Flags.isOp(user) && !existsOperator()) {
        val designateeActor = designatedActors.getOrElse(actor, users.head._1)
        val designatedUser = users(designateeActor)

        val oppedUser = Flags.op(designatedUser)
        users += designateeActor -> oppedUser
        designatedActors -= actor
        designateeActor ! UserUpdated(oppedUser)
        localUsers ! UserFlags(oppedUser)
      }
    })

    userOpt
  }

  override def designate(actor: ActorRef, designatee: ActorRef) = {
    users.get(actor).fold()(user => {
      actor !
        (if (Flags.isOp(user)) {
          users.get(designatee).fold[ChatEvent](UserError(INVALID_USER))(designatedUser => {
            designatedActors += actor -> designatee
            super.designate(actor, designatee)
            UserInfo(USER_DESIGNATED(designatedUser.name))
          })
        } else {
          UserError(NOT_OPERATOR)
        })
    })
  }

  override def remoteRem(actor: ActorRef) = {
    val userOpt = super.remoteRem(actor)

    userOpt.fold()(user => {
      if (users.nonEmpty && Flags.isOp(user) && !existsOperator()) {
        val designateeActor = designatedActors.getOrElse(actor, users.head._1)
        val designatedUser = users(designateeActor)

        val oppedUser = Flags.op(designatedUser)
        users += designateeActor -> oppedUser
        designatedActors -= actor
        localUsers ! UserFlags(oppedUser)
      }
    })

    userOpt
  }

  def existsOperator(): Boolean = {
    // O(n) sadface
    users
      .values
      .foreach(user => {
        if (Flags.isOp(user)) {
          return true
        }
      })
    false
  }
}
