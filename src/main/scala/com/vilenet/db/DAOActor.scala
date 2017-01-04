package com.vilenet.db

import akka.actor.Props
import com.vilenet.Constants._
import com.vilenet.{ViLeNetComponent, ViLeNetRemotingActor}
import com.vilenet.coders.commands.Command
import com.vilenet.servers.Remotable

/**
  * Created by filip on 12/21/15.
  */
object DAOActor extends ViLeNetComponent {
  def apply() = system.actorOf(Props[DAOActor], VILE_NET_DAO_PATH)
}

case class GetAccount(username: String) extends Command
case class CreateAccount(username: String, passwordHash: Array[Byte]) extends Remotable
case class UpdateAccountPassword(username: String, passwordHash: Array[Byte]) extends Remotable
case class CloseAccount(username: String, reason: String = "") extends Remotable
case class OpenAccount(username: String) extends Remotable
case class DAOCreatedAck(username: String, passwordHash: Array[Byte]) extends Command
case class DAOUpdatedPasswordAck(username: String, passwordHash: Array[Byte]) extends Command
case class DAOClosedAccountAck(username: String, reason: String) extends Command
case class DAOOpenedAccountAck(username: String) extends Command

class DAOActor extends ViLeNetRemotingActor {

  override val actorPath = VILE_NET_DAO_PATH

  override def receive: Receive = {
    case CreateAccount(username, passwordHash) =>
      DAO.createUser(username, passwordHash)
      if (isLocal()) {
        sender() ! DAOCreatedAck(username, passwordHash)
      }

    case UpdateAccountPassword(username, passwordHash) =>
      DAO.updateUser(username, passwordHash)
      if (isLocal()) {
        sender() ! DAOUpdatedPasswordAck(username, passwordHash)
      }

    case CloseAccount(username, reason) =>
      DAO.updateUser(username, closed = Some(true), closedReason = Some(reason))
      if (isLocal()) {
        sender() ! DAOClosedAccountAck(username, reason)
      }

    case OpenAccount(username) =>
      DAO.updateUser(username, closed = Some(false), closedReason = Some(""))
      if (isLocal()) {
        sender() ! DAOOpenedAccountAck(username)
      }
  }
}
