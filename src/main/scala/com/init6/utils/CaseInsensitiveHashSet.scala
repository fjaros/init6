package com.init6.utils

import scala.collection.mutable

/**
  * Created by filip on 12/3/15.
  */
object CaseInsensitiveHashSet {
  def apply() = new CaseInsensitiveHashSet
}

sealed class CaseInsensitiveHashSet extends mutable.HashSet[String] {
  override def addElem(elem: String): Boolean = super.addElem(elem.toLowerCase)
  override def contains(elem: String): Boolean = super.contains(elem.toLowerCase)
  override def removeElem(elem: String): Boolean = super.removeElem(elem.toLowerCase)
}
