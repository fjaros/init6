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

  val name = args(0)
  val splt = args(1).split(":")
  val (host, port) = (splt(0), if (splt.length > 1) splt(1).toInt else 6112)

  DAO
  DAOActor()
  ServerPantyDropper(name)
  IpLimitActor(8)
  UsersActor()
  ChannelsActor()

  sys.addShutdownHook({
    system.terminate()
    DAO.close()
  })

  val bind = new InetSocketAddress(host, port)
  ConnectionHandler(bind)
}
