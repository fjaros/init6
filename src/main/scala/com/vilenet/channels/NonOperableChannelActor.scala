package com.vilenet.channels

import akka.actor.ActorRef
import com.vilenet.Constants._
import com.vilenet.coders.commands.OperableCommand
import com.vilenet.users.UserToChannelCommandAck

/**
  * Created by filip on 11/25/15.
  */
trait NonOperableChannelActor extends ChannelActor {

  override def receiveEvent = ({
    case command: UserToChannelCommandAck =>
      command.command match {
        case command: OperableCommand => sender() ! UserError(NOT_OPERATOR)
        case _ => super.receiveEvent(command)
      }
    case command: OperableCommand =>
      val userActor = sender()
      users.get(userActor).fold(userActor ! UserError(NOT_OPERATOR))(user => {
        if (Flags.isAdmin(user)) {
          super.receiveEvent(command)
        } else {
          userActor ! UserError(NOT_OPERATOR)
        }
      })
  }: Receive)
    .orElse(super.receiveEvent)

  override def add(actor: ActorRef, user: User): User = {
    val newUser = Flags.deOp(user)

    super.add(actor, newUser)
  }
}
