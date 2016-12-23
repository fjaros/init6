package com.vilenet.db

import akka.actor.Props
import com.vilenet.Constants._
import com.vilenet.{ViLeNetClusterActor, ViLeNetComponent}
import com.vilenet.coders.commands.Command

/**
  * Created by filip on 12/21/15.
  */
object DAOActor extends ViLeNetComponent {
  def apply() = system.actorOf(Props[DAOActor], VILE_NET_DAO_PATH)
}

case class GetAccount(username: String) extends Command
case class CreateAccount(username: String, passwordHash: Array[Byte]) extends Command
case class UpdateAccount(username: String, passwordHash: Array[Byte], flags: Int = -1) extends Command
case class DAOCreatedAck(username: String, passwordHash: Array[Byte]) extends Command
case class DAOUpdatedAck(username: String, passwordHash: Array[Byte]) extends Command

class DAOActor extends ViLeNetClusterActor {

  subscribe(TOPIC_DAO)

  override def receive: Receive = {
    case CreateAccount(username, passwordHash) =>
      DAO.createUser(username, passwordHash)
      if (isLocal()) {
        sender() ! DAOCreatedAck(username, passwordHash)
      }

    case UpdateAccount(username, passwordHash, flags) =>
      DAO.updateUser(username, passwordHash, flags)
      if (isLocal()) {
        sender() ! DAOUpdatedAck(username, passwordHash)
      }
  }
}
