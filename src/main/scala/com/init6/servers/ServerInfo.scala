package com.init6.servers

import com.init6.Init6Actor

/**
  * Created by filip on 12/6/15.
  */
class ServerInfo extends Init6Actor {

  override def receive: Receive = {
    case x => //println(s"SERVERINFO $x")
  }
}
