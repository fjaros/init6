package com.init6.coders

/**
  * Created by filip on 2/7/17.
  */
object IPUtils {

  def bytesToDword(ip: Array[Byte]) = {
    (ip(0) & 0x000000FF) |
    (ip(1) << 8 & 0x0000FF00) |
    (ip(2) << 16 & 0x00FF0000) |
    (ip(3) << 24 & 0xFF000000)
  }

  def dwordToString(ip: Int) = {
    (ip & 0xFF) + "." +
    (ip >> 8 & 0xFF) + "." +
    (ip >> 16 & 0xFF) + "." +
    (ip >> 24 & 0xFF)
  }

  def stringToBytes(ip: String) = {
    ip.split("\\.").map(_.toByte)
  }
}
