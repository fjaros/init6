package com.init6

import akka.actor.Actor.Receive
import akka.actor.{ActorContext, FSM, Props}

/**
  * Created by filip on 4/2/17.
  */
object Init6Test extends App {//with Init6Component {


  new Test2()

//sys.exit()
//  val fa = system.actorOf(Props[FirstActor])
//
//  fa ! Test1
//  Thread.sleep(1000)
//  fa ! Test2
}


trait FirstState
case object FirstState1 extends FirstState
case object FirstState2 extends FirstState

case object Test1
case object Test2

class FirstActor extends Init6Actor {//with FSM[FirstState, Any] {

//  override def preStart = {
//    super.preStart()
//
//    startWith(FirstState1, null)
//  }
//
//  when(FirstState1) {
//    case Event(Test1, _) =>
//      println("FirstState1")
//      context.become(new BinaryMessageReceiver().receive)
//      goto(FirstState2)
//  }
//
//  when(FirstState2) {
//    case _ =>
//      println("FirstState2")
//      stay()
//  }
  override def receive = {
    case Test1 =>
      println("FirstState1")
      context.become(new BinaryMessageReceiver()(context).receive)
  }
}

class BinaryMessageReceiver()(override val context: ActorContext) extends FSM[FirstState, Any] {

  startWith(FirstState1, null)

  when(FirstState1) {
    case _ =>
      println("Test")
      stay()
  }
}

trait Test {

  val context: Unit = {
    println("Test22")
  }
}

class Test2(override val context: Unit = { println("Shit") }) extends Test {

  /*override val context: Unit = {
    println("Shit")
  }*/
}