package com.init6

import akka.actor.Actor.Receive
import akka.actor.{ActorContext, FSM, Props}
import akka.util.ByteString
import com.init6.connection.{BinaryReceiver, ChatReceiver}

/**
  * Created by filip on 4/2/17.
  */
object Init6Test extends App {

  val x = new ChatReceiver

  x.parsePacket(ByteString("C1\nC2")).foreach(println)
  x.parsePacket(ByteString("\nC3\n")).foreach(println)
}