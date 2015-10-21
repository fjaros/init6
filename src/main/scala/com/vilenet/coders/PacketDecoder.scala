package com.vilenet.coders

import akka.util.ByteString

/**
 * Created by filip on 9/19/15.
 */
protected[coders] trait PacketDecoder[A] {
  def decode(bytes: ByteString): A
}
