package com.vilenet.utils

import scala.collection.mutable
import scala.util.Try

/**
 * Created by filip on 10/11/15.
 */
object CaseInsensitiveHashMap {
  def apply[B]() = new CaseInsensitiveHashMap[B]()
}

sealed class CaseInsensitiveHashMap[B] extends mutable.HashMap[String, B] {
  override def +=(kv: (String, B)): this.type = super.+=(kv.copy(kv._1.toLowerCase, kv._2))
  override def -=(key: String): this.type = super.-=(key.toLowerCase)
  override def apply(key: String): B = get(key).get
  override def contains(key: String): Boolean = super.contains(key.toLowerCase)
  override def get(key: String): Option[B] = super.get(key.toLowerCase)
  override def remove(key: String): Option[B] = super.remove(key.toLowerCase)
}

object RealKeyedCaseInsensitiveHashMap {
  def apply[B]() = new RealKeyedCaseInsensitiveHashMap[B]
}

sealed class RealKeyedCaseInsensitiveHashMap[B] extends CaseInsensitiveHashMap[B] {

  var realKeyMap = mutable.Map[String, String]()

  override def +=(kv: (String, B)): this.type = {
    realKeyMap += kv._1.toLowerCase -> kv._1
    super.+=(kv)
  }

  override def -=(key: String): this.type = {
    realKeyMap -= key.toLowerCase
    super.-=(key.toLowerCase)
  }

  override def remove(key: String): Option[B] = {
    realKeyMap -= key.toLowerCase
    super.remove(key.toLowerCase)
  }

  def getWithRealKey(key: String): Option[(String, B)] = {
    (for {
      realKey <- Try(realKeyMap(key.toLowerCase))
      value <- Try(apply(key))
    } yield {
      (realKey, value)
    })
      .toOption
  }
}

object CaseInsensitiveMultiMap {
  def apply[B]() = new CaseInsensitiveMultiMap[B]
}

sealed class CaseInsensitiveMultiMap[B] extends CaseInsensitiveHashMap[mutable.Set[B]] with mutable.MultiMap[String, B] {

  def +=(kv: (String, B)): this.type = addBinding(kv._1.toLowerCase, kv._2)
  def foreach[C](key: String, f: ((String, B)) => C): Unit = get(key).fold()(_.foreach(f(key, _)))
}
