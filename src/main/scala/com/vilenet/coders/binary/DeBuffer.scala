package com.vilenet.coders.binary

import akka.util.ByteString

/**
  * Created by filip on 12/7/15.
  */
object DeBuffer {
  def apply(data: ByteString) = new DeBuffer(data)
}

sealed class DeBuffer(data: ByteString) {
  val index = 0

  def byte(index: Int = index): Byte = {
    data(index)
  }

  def word(index: Int = index): Short = {
    (data(index + 1) << 8 & 0xff00 | data(index) & 0xff).toShort
  }

  def dword(index: Int = index): Int = {
    data(index + 3) << 24 & 0xff000000 | data(index + 2) << 16 & 0xff0000 | data(index + 1) << 8 & 0xff00 | data(index) & 0xff
  }

  def byteArray(index: Int = index, length: Int = 4) = {
    data.slice(index, index + length).toArray
  }
}
