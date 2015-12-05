package com.vilenet.utils

import scala.collection.mutable.ArrayBuffer

/**
  * Created by filip on 12/4/15.
  */
object FiniteArrayBuffer {
  val DEFAULT_LIMIT = 25

  def apply[A](limit: Int = DEFAULT_LIMIT) = new FiniteArrayBuffer[A](limit)
}

sealed class FiniteArrayBuffer[A](limit: Int) extends ArrayBuffer[A](limit) {
  override def +=(elem: A): this.type = {
    if (initialSize > size) {
      super.+=(elem)
    } else {
      this
    }
  }

  def getInitialSize = initialSize
}
