package com.vilenet.coders

import akka.util.ByteString

/**
 * Created by filip on 9/20/15.
 */
trait AkkaEncoder {
  implicit protected def encode(data: String): ByteString = ByteString(data)
  implicit protected def encode(data: Array[Byte]): ByteString = ByteString(data)
}
