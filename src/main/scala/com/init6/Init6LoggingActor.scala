package com.init6

import akka.event.LoggingReceive

/**
  * Created by filip on 1/7/17.
  */
trait Init6LoggingActor extends Init6Actor {

  def receive = LoggingReceive(loggedReceive)

  def loggedReceive: Receive
}
