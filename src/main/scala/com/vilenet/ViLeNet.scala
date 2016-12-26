package com.vilenet

import java.net.InetSocketAddress
import java.util.concurrent.TimeUnit

import akka.util.Timeout
import com.vilenet.channels.ChannelsActor
import com.vilenet.connection.{ConnectionHandler, IpLimitActor}
import com.vilenet.db.{DAO, DAOActor}
import com.vilenet.servers.ServerPantyDropper
import com.vilenet.users.UsersActor

import scala.concurrent.Await

/**
 * Created by filip on 9/19/15.
 */
object ViLeNet extends App with ViLeNetComponent {

  DAO
  DAOActor()
  ServerPantyDropper(Config.Server.name)
  IpLimitActor(200)
  UsersActor()
  ChannelsActor()

  sys.addShutdownHook({
    implicit val timeout = Timeout(5, TimeUnit.SECONDS)
    Await.ready(system.terminate(), timeout.duration)
    DAO.close()
  })

  val bind = new InetSocketAddress(Config.Server.host, Config.Server.port)
  ConnectionHandler(bind)
}
