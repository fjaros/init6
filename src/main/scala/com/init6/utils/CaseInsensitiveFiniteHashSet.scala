package com.init6.utils

import scala.collection.mutable

/**
  * Created by filip on 11/11/15.
  */
object CaseInsensitiveFiniteHashSet {
  def apply(limit: Int) = new CaseInsensitiveFiniteHashSet(limit)
}

sealed class CaseInsensitiveFiniteHashSet(limit: Int) extends mutable.LinkedHashSet[String] {

  override def contains(elem: String): Boolean = super.contains(elem.toLowerCase)
  override def remove(elem: String): Boolean = super.remove(elem.toLowerCase)

  override def add(elem: String): Boolean = {
    if (size == limit) {
      super.remove(head)
    }
    super.add(elem.toLowerCase)
  }
}
