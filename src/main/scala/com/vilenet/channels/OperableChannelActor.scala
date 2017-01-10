package com.vilenet.channels

import akka.actor.ActorRef
import com.vilenet.Constants._
import com.vilenet.coders.commands.{DesignateCommand, OperableCommand}
import com.vilenet.users.{UserToChannelCommandAck, UserUpdated}

import scala.collection.mutable

/**
  * Created by filip on 11/24/15.
  */
trait OperableChannelActor extends ChannelActor {

  val designatedActors = mutable.HashMap[ActorRef, ActorRef]()

  override def receiveEvent = ({
    case command: UserToChannelCommandAck =>
      val userActor = sender()
      users.get(userActor).foreach(user => {
        if (Flags.canBan(user)) {
          command.command match {
            case DesignateCommand(_, designatee) =>
              designate(userActor, command.userActor)
            case _ =>
          }
        } else {
          command.command match {
            case _: OperableCommand => sender() ! UserError(NOT_OPERATOR)
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

    userOpt.foreach(user => {
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

  def existsOperator(): Boolean = {
    // O(n) sadface
    !users.values.forall(!Flags.isOp(_))
  }
}
