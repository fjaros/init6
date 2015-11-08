package com.vilenet

import akka.actor.{Props, Actor}
import akka.testkit.TestActorRef

import scala.collection.mutable

/**
 * Created by filip on 11/6/15.
 */
object MockActor extends ViLeNetTestComponent {
  def apply(eventQueue: mutable.SynchronizedQueue[Any]) = TestActorRef[MockActor](Props(new MockActor(eventQueue)))
}

class MockActor(eventQueue: mutable.SynchronizedQueue[Any]) extends Actor with ViLeNetTestComponent {

  override def receive: Receive = {
    case event => eventQueue += event
  }
}
