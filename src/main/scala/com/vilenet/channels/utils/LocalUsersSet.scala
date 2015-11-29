package com.vilenet.channels.utils

import akka.actor.ActorRef

import scala.collection.mutable

/**
  * Created by filip on 11/14/15.
  */
object LocalUsersSet {
  def apply() = new LocalUsersSet
}

sealed class LocalUsersSet extends mutable.HashSet[ActorRef] {
  def !(message: Any)(implicit sender: ActorRef): Unit = foreach(_ ! message)
}
