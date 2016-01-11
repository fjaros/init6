package com.vilenet

import java.net.InetSocketAddress

import com.vilenet.channels.ChannelsActor
import com.vilenet.connection.{IpLimitActor, ConnectionHandler}
import Constants.VILE_NET
import com.vilenet.db.{DAO, DAOActor}
import com.vilenet.servers.ServerColumbus
import com.vilenet.users.UsersActor

import scala.io.StdIn

/**
 * Created by filip on 9/19/15.
 */
object ViLeNet extends App with ViLeNetComponent {

  val name = args(0)
  val splt = args(1).split(":")
  val (host, port) = (splt(0), if (splt.length > 1) splt(1).toInt else 6112)

  DAO
  DAOActor()
  ServerColumbus(name)
  IpLimitActor(8)
  UsersActor()
  ChannelsActor()

  val bind = new InetSocketAddress(host, port)
  ConnectionHandler(bind)

  StdIn.readLine(s"Hit ENTER to exit ...${System.getProperty("line.separator")}")

  system.terminate()
  DAO.close()
}
