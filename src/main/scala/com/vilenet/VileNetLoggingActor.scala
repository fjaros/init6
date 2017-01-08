package com.vilenet

import akka.event.LoggingReceive

/**
  * Created by filip on 1/7/17.
  */
trait VileNetLoggingActor extends ViLeNetActor {

  def receive = LoggingReceive(loggedReceive)

  def loggedReceive: Receive
}
