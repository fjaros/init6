package com.init6.users

import java.util.Comparator

import akka.actor.ActorSystem
import akka.dispatch.{Envelope, UnboundedStablePriorityMailbox}
import com.typesafe.config.Config

/**
  * Created by filip on 3/31/17.
  */
class UsersMailbox(settings: ActorSystem.Settings, config: Config)
  extends UnboundedStablePriorityMailbox(UsersPriorityGenerator)

object UsersPriorityGenerator extends Comparator[Envelope] {

  override def compare(o1: Envelope, o2: Envelope) = {
    if (o1.message.isInstanceOf[Add] && o2.message.isInstanceOf[Add]) {
      val m1 = o1.message.asInstanceOf[Add]
      val m2 = o2.message.asInstanceOf[Add]

      if (m1.connectionInfo.connectedTime > m2.connectionInfo.connectedTime) {
        1
      } else {
        -1
      }
    } else {
      0
    }
  }
}
