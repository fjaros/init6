package com.vilenet.coders

import akka.util.ByteString

/**
 * Created by filip on 9/20/15.
 */
trait Encoder {
  implicit protected def encode(data: String): ByteString = ByteString(data)
  implicit protected def encode(data: Array[Byte]): ByteString = ByteString(data)
  implicit protected def encodeToOption(data: String): Option[ByteString] = Some(data)
  implicit protected def encodeToOption(data: ByteString): Option[ByteString] = Some(data)


  def apply(data: Any): Option[ByteString]
}
