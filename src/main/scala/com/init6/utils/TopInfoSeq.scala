package com.init6.utils

import akka.actor.ActorRef
import com.init6.users.TopInfo

import scala.collection.mutable

/**
  * Created by filip on 12/4/15.
  */
object TopInfoSeq {
  val DEFAULT_LIMIT = 25

  def apply(limit: Int = DEFAULT_LIMIT) = new TopInfoSeq(limit)
}

// Should be implemented using a MinMaxPriorityQueue (PriorityDeque)
// Doesn't seem scala offers any such thing so let's cheese it
sealed class TopInfoSeq(val limit: Int) {

  private val alreadyLogged = mutable.HashSet.empty[ActorRef]
  private var _l = mutable.PriorityQueue.empty[TopInfo](Ordering.by[TopInfo, Long](_.connectionInfo.connectedTime).reverse)
  private var lastConnectionTime: Long = 0

  def +=(elem: TopInfo): Unit = {
    if (!alreadyLogged.contains(elem.connectionInfo.actor)) {
      if (limit > _l.size) {
        _l += elem
        alreadyLogged += elem.connectionInfo.actor

        if (elem.connectionInfo.connectedTime > lastConnectionTime) {
          lastConnectionTime = elem.connectionInfo.connectedTime
        }

      } else if (elem.connectionInfo.connectedTime < lastConnectionTime) {
        _l += elem
        alreadyLogged += elem.connectionInfo.actor

        // gross as fuck
        val oldQ = _l.dequeueAll
        oldQ.dropRight(1).foreach(_l += _)
      }
    }
  }

  // also gross
  def values: Seq[TopInfo] = _l.clone().dequeueAll
}
