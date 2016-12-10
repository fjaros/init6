package com.vilenet

import java.net.InetSocketAddress

import com.vilenet.channels.ChannelsActor
import com.vilenet.connection.{IpLimitActor, ConnectionHandler}
import com.vilenet.db.{DAO, DAOActor}
import com.vilenet.servers.ServerPantyDropper
import com.vilenet.users.UsersActor

/**
 * Created by filip on 9/19/15.
 */
object ViLeNet extends App with ViLeNetComponent {

  DAO
  DAOActor()
  ServerPantyDropper(Config.Server.name)
  IpLimitActor(10000)
  UsersActor()
  ChannelsActor()

  sys.addShutdownHook({
    system.terminate()
    DAO.close()
  })

  val bind = new InetSocketAddress(Config.Server.host, Config.Server.port)
  ConnectionHandler(bind)
}
