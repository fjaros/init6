package com.vilenet.channels.utils

import akka.actor.ActorRef
import com.vilenet.servers.RemoteEvent

import scala.annotation.tailrec
import scala.collection.mutable

/**
  * Created by filip on 11/14/15.
  */
object RemoteMultiMap {
  def apply[A, B]() = new RemoteMultiMap[A, B]
}

sealed class RemoteMultiMap[A, B] extends mutable.HashMap[A, mutable.Set[B]] with mutable.MultiMap[A, B] {

  def +=(kv: (A, B)): this.type = addBinding(kv._1, kv._2)
  def +=(key: A): this.type = {
    if (!contains(key)) {
      +=(key -> mutable.Set[B]())
    } else {
      this
    }
  }

  def ++=(kv: (A, Iterable[B])): this.type = {
    @tailrec
    def loop(kv: (A, Iterable[B])): Unit = {
      if (nonEmpty) {
        +=(kv._1 -> kv._2.head)
        loop(kv._1 -> kv._2.tail)
      }
    }
    loop(kv)
    this
  }
}


object RemoteChannelsMultiMap {
  def apply() = new RemoteChannelsMultiMap
}

sealed class RemoteChannelsMultiMap extends RemoteMultiMap[ActorRef, ActorRef] {

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
