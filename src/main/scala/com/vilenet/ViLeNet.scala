package com.vilenet

import java.util.concurrent.TimeUnit

import akka.actor.PoisonPill
import com.vilenet.channels.ChannelsActor
import com.vilenet.connection.{ConnectionHandler, IpLimitActor}
import com.vilenet.db.{DAO, DAOActor}
import com.vilenet.servers.{ServerPantyDropper, ServerRegistry}
import com.vilenet.users.UsersActor

import scala.concurrent.Await
import scala.concurrent.duration.Duration

/**
 * Created by filip on 9/19/15.
 */
object ViLeNet extends App with ViLeNetComponent {

  DAO
  ServerRegistry()
  DAOActor()
  ServerPantyDropper(Config.Server.name)
  IpLimitActor(200)
  UsersActor()
  ChannelsActor()

  val connectionHandlerActor = ConnectionHandler(Config.Server.host, Config.Server.port)

  sys.addShutdownHook({
    connectionHandlerActor ! PoisonPill

    implicit val timeout = Duration(10, TimeUnit.SECONDS)
    Await.ready(system.terminate(), timeout)
    DAO.close()
  })
}
