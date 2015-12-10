package com.vilenet.coders.binary

import java.nio.ByteOrder

import akka.util.ByteString
import com.vilenet.Constants.CHARSET

/**
  * Created by filip on 12/7/15.
  */
object DeBuffer {
  def apply(data: ByteString) = new DeBuffer(data)
}

sealed class DeBuffer(data: ByteString) {

  val buffer = data.asByteBuffer.order(ByteOrder.LITTLE_ENDIAN)
  var index = 0

  def skip(length: Int) = {
    index += length
  }

  def byte(index: Int = index): Byte = {
    val ret = buffer.get(index)
    this.index += 1
    ret
  }

  def word(index: Int = index): Short = {
    val ret = buffer.getShort(index)
    this.index += 2
    ret
  }

  def dword(index: Int = index): Int = {
    val ret = buffer.getInt(index)
    this.index += 4
    ret
  }

  def byteArray(length: Int): Array[Byte] = {
    byteArray(index, length)
  }

  def byteArray(index: Int = index, length: Int = 0) = {
    val ret = data.slice(index, index + length).toArray
    this.index += length
    ret
  }

  def byteArrayAsString(length: Int): String = {
    byteArrayAsString(index, length)
  }

  def byteArrayAsString(index: Int = index, length: Int = 0) = {
    val ret = new String(byteArray(index, length), CHARSET)
    this.index += length
    ret
  }

  def string(index: Int = index): String = {
    val ret = data.drop(index).takeWhile(_ != 0).toArray
    this.index += ret.length + 1
    new String(ret, CHARSET)
  }
}
