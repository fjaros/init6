package com.init6.channels

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.testkit.TestActorRef
import com.init6.utils.RealKeyedCaseInsensitiveHashMap
import org.scalatest.{Matchers, FlatSpec}

/**
  * Created by filip on 1/17/16.
  */
class ChannelsActorSpec extends FlatSpec with Matchers {

  implicit val system = ActorSystem()

  def actor = TestActorRef(Props(classOf[ChannelsActor]))

  "x" should "y" in {
    val x = (1 to 100).map(x => actor)

    val test = RealKeyedCaseInsensitiveHashMap[ActorRef]()

    x.zipWithIndex
      .foreach {
        case (actor, i) =>
          test += String.valueOf(i) -> actor
      }

    test -= "40"

    println (test.size)
  }
}
