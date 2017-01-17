package com.init6.utils

import scala.collection.mutable

/**
  * Created by filip on 12/4/15.
  */
object FiniteLinkedHashMap {
  val DEFAULT_LIMIT = 25

  def apply[A, B](limit: Int = DEFAULT_LIMIT) = new FiniteLinkedHashMap[A, B](limit)
}

sealed class FiniteLinkedHashMap[A, B](limit: Int) extends mutable.LinkedHashMap[A, B] {

  override protected def initialSize = limit

  override def put(key: A, value: B) = {
    if (initialSize > size || contains(key)) {
      super.put(key, value)
    } else {
      None
    }
  }

  def getInitialSize = initialSize
}
