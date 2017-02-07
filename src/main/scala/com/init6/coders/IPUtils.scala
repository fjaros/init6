package com.init6.coders

import java.net.InetAddress

/**
  * Created by filip on 2/7/17.
  */
object IPUtils {

  def toDword(ip: Array[Byte]) = {
    ((ip(3) << 24) & 0xFF000000) |
    ((ip(2) << 16) & 0x00FF0000) |
    ((ip(1) << 8) & 0x0000FF00) |
    (ip(0) & 0x000000FF)
  }

  def toString(ip: Int) = {
    val bytes = new Array[Byte](4)
    bytes(3) = ((ip & 0xFF000000) >> 24).toByte
    bytes(2) = ((ip & 0x00FF0000) >> 16).toByte
    bytes(1) = ((ip & 0x0000FF00) >> 8).toByte
    bytes(0) = (ip & 0x000000FF).toByte
    InetAddress.getByAddress(bytes).getHostAddress
  }
}
