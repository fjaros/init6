package com.vilenet

import java.net.InetSocketAddress

import com.vilenet.channels.ChannelsActor
import com.vilenet.coders.binary.hash.BSHA1
import com.vilenet.connection.ConnectionHandler
import Constants.VILE_NET
import com.vilenet.db.DAO
import com.vilenet.servers.ServerColumbus
import com.vilenet.users.UsersActor

import scala.io.StdIn

/**
 * Created by filip on 9/19/15.
 */
object ViLeNet extends App with ViLeNetComponent {

  var port = 6112

  if (args.length > 1) {
    port = args(1).toInt
  }

  //DAO.createUser("l2k-shadow", passwordHash = BSHA1("vile123456"))
  //DAO.createUser("boatbawt", passwordHash = BSHA1("boatbawt#$&(*)!@vsdh9ai@$!Q^911"))
  DAO
  ServerColumbus(args(0))
  UsersActor()
  ChannelsActor()

  val bind = new InetSocketAddress("0.0.0.0", port)
  system.actorOf(ConnectionHandler(bind, Array(args(0))), VILE_NET)

  StdIn.readLine(s"Hit ENTER to exit ...${System.getProperty("line.separator")}")
  system.terminate()
  DAO.close()
}
