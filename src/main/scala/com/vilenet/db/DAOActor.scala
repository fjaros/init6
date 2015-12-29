package com.vilenet.db

import akka.actor.Props
import akka.cluster.pubsub.DistributedPubSubMediator.Subscribe
import com.vilenet.Constants._
import com.vilenet.coders.commands.Command
import com.vilenet.{ViLeNetActor, ViLeNetComponent}

/**
  * Created by filip on 12/21/15.
  */
object DAOActor extends ViLeNetComponent {
  def apply() = system.actorOf(Props(new DAOActor), VILE_NET_DAO_PATH)
}

case class GetAccount(username: String) extends Command
case class CreateAccount(username: String, passwordHash: Array[Byte]) extends Command
case class UpdateAccount(username: String, passwordHash: Array[Byte], flags: Int = -1) extends Command
case class DAOAck(username: String, passwordHash: Array[Byte]) extends Command

class DAOActor extends ViLeNetActor {

  mediator ! Subscribe(TOPIC_DAO, self)

  override def receive: Receive = {
    case CreateAccount(username, passwordHash) =>
      DAO.createUser(username, passwordHash)
      if (isLocal()) {
        sender() ! DAOAck(username, passwordHash)
      }

    case UpdateAccount(username, passwordHash, flags) =>
      DAO.updateUser(username, passwordHash, flags)
      if (isLocal()) {
        sender() ! DAOAck(username, passwordHash)
      }
  }
}
