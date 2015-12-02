package com.vilenet.channels.utils

import akka.actor.ActorRef

import scala.collection.mutable

/**
  * Created by filip on 11/14/15.
  */
object RemoteChannelsMultiMap {
  def apply() = new RemoteChannelsMultiMap
}

sealed class RemoteChannelsMultiMap
  extends mutable.HashMap[ActorRef, mutable.Set[ActorRef]] with mutable.MultiMap[ActorRef, ActorRef] {

  def +=(kv: (ActorRef, ActorRef)): this.type = addBinding(kv._1, kv._2)
  def +=(key: ActorRef): this.type = +=(key -> mutable.Set[ActorRef]())
  def !(message: Any)(implicit sender: ActorRef): Unit = {
    message match {
      case Unit =>
      case _ => keys.foreach(_ ! RemoteEvent(message))
    }
  }

  def tell(message: Any, sender: ActorRef): Unit = {
    this.!(message)(sender)
  }
}
