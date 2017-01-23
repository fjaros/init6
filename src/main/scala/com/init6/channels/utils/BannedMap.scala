package com.init6.channels.utils

import akka.actor.ActorRef

import scala.collection.mutable

/**
  * Created by filip on 1/20/17.
  */
object BannedMap {
  def apply(limit: Int) = {
    if (limit <= 0) {
      throw new IllegalArgumentException("BannedMap limit must be > 0")
    }
    new BannedMap(limit)
  }
}

sealed class BannedMap(limit: Int) extends mutable.HashMap[ActorRef, mutable.LinkedHashSet[String]] {

  def apply(value: String): Boolean = {
    val lCaseValue = value.toLowerCase
    !values.forall(!_.contains(lCaseValue))
  }

  def +=(value: (ActorRef, String)): BannedMap = {
    val bannedSet = get(value._1)
      .getOrElse({
        val bannedSet = mutable.LinkedHashSet.empty[String]
        this += value._1 -> bannedSet
        bannedSet
      })

    if (bannedSet.size >= limit) {
      bannedSet -= bannedSet.head
    }

    bannedSet += value._2.toLowerCase
    this
  }

  def ++=(bannedMap: Seq[(ActorRef, Seq[String])]) = {
    bannedMap.foreach {
      case (actor, bannedSeq) =>
        bannedSeq.foreach(banned => +=(actor -> banned))
    }
  }

  def -=(key: ActorRef, value: String) = {
    val lCaseValue = value.toLowerCase
    get(key).foreach(_ -= lCaseValue)
  }

  def -=(value: String) = {
    val lCaseValue = value.toLowerCase
    values.foreach(_ -= lCaseValue)
  }

  def toImmutable: Seq[(ActorRef, Seq[String])] = {
    mapValues(_.toSeq).toSeq
  }
}
