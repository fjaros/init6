package com.init6.channels

import akka.actor.ActorRef
import com.init6.Constants._
import com.init6.coders.commands.OperableCommand
import com.init6.users.{GetUsers, UserToChannelCommandAck}

/**
  * Created by filip on 11/25/15.
  */
trait NonOperableChannelActor extends ChannelActor {

  override def receiveEvent = ({
    case command: UserToChannelCommandAck =>
      command.command match {
        case command: OperableCommand =>
          handleOperableCommand(command)
        case _ => super.receiveEvent(command)
      }

    case command: OperableCommand =>
      handleOperableCommand(command)

    case command @ GetUsers =>
      super.receiveEvent(command)
      sender() ! UserInfo(PUBLIC_CHANNEL)
  }: Receive)
    .orElse(super.receiveEvent)

  override def add(actor: ActorRef, user: User): User = {
    super.add(actor, Flags.deOp(user))
  }

  def handleOperableCommand(command: OperableCommand) = {
    val userActor = sender()
    users.get(userActor).fold({
      if (isLocal()) {
        userActor ! UserError(NOT_OPERATOR)
      }
    })(user => {
      if (Flags.isAdmin(user)) {
        super.receiveEvent(command)
      } else {
        if (isLocal()) {
          userActor ! UserError(NOT_OPERATOR)
        }
      }
    })
  }
}
