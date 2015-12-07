package com.vilenet.servers

import com.vilenet.ViLeNetActor
import com.vilenet.users.UsersActor

/**
  * Created by filip on 12/6/15.
  */
class ServerInfo extends ViLeNetActor {

  override def receive: Receive = {
    case x => println(s"SERVERINFO $x")
  }
}
