package com.init6.utils

import scala.collection.mutable

/**
 * Created by filip on 9/28/15.
 */
object CaseInsensitiveLinkedHashMap {
  def apply[B]() = new CaseInsensitiveLinkedHashMap[B]()
}

sealed class CaseInsensitiveLinkedHashMap[B] extends mutable.LinkedHashMap[String, B] {
  override def get(key: String): Option[B] = super.get(key.toLowerCase)
  override def put(key: String, value: B): Option[B] = super.put(key.toLowerCase, value)
  override def remove(key: String): Option[B] = super.remove(key.toLowerCase)
}
