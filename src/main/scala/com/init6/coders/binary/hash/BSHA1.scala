package com.init6.coders.binary.hash

import scala.annotation.switch

/**
  * This is an implementation of the Broken SHA1 hashing that battle.net uses for various packets (including passwords,
  * cdkey, etc). It hashes the passed in data down to 5 bytes (160 bits). It uses an algorithm that is very similar to
  * the standard SHA-1 hashing, except is a little different.
  * <P>
  * Part of the credit for this goes to Yobguls - this is based off his code.
  *
  * @author iago
  * @author filip (Logical performance improvements + ported to Scala -- as easy as that actually is)
  *
  */
object BSHA1 {

  /* -- Utility Functions -- */
  private val BLOCK_SIZE = 0x40
  private val HASH_BUFFER_LENGTH = 0x10 + 5

  val newHashArray = {
    val ret = new Array[Int](HASH_BUFFER_LENGTH)
    ret.update(0, 0x67452301)
    ret.update(1, 0xEFCDAB89)
    ret.update(2, 0x98BADCFE)
    ret.update(3, 0x10325476)
    ret.update(4, 0xC3D2E1F0)
    ret
  }

  implicit def intArrayToByteArray(data: Array[Int]): Array[Byte] = {
    data.flatMap(intToByteArray)
  }

  def intToByteArray(int: Int): Array[Byte] = {
    Array(
      (int & 0x000000FF).toByte,
      ((int & 0x0000FF00) >>> 8).toByte,
      ((int & 0x00FF0000) >>> 16).toByte,
      ((int & 0xFF000000) >>> 24).toByte
    )
  }


  /**
    * Hash for password
    */
  def apply(data: String): Array[Byte] = {
    calcHashBuffer(data.map(_.toByte).toArray)
  }

  def apply(data: Array[Byte]): Array[Byte] = {
    calcHashBuffer(data)
  }

  /**
    * Double Hash for password
    * clientToken + serverToken + passwordHash + '\0'
    */
  def apply(clientToken: Int, serverToken: Int, data: Array[Byte]): Array[Byte] = {
    calcHashBuffer(intToByteArray(clientToken) ++ intToByteArray(serverToken) ++ data :+ 0.toByte)
  }

  def apply(data: String, dataToMatch: Array[Byte]): Boolean = {
    apply(data).sameElements(dataToMatch)
  }

  /**
    * Calculates the 20 byte hash based on the passed in byte[] data.
    *
    * @param hashData
     * The data to hash.
    * @return The 20 bytes of hashed data. Note that this array is actually 60 bytes long, but the last 40 bytes should
    *         just be ignored.
    */
   private def calcHashBuffer(hashData: Array[Byte]): Array[Int] = {
    val hashBuffer = newHashArray.clone()
    val hashDataLength = hashData.length

    // While loops are faster than for loops
    var i = 0
    while (i < hashDataLength) {
      val tailLength = hashDataLength - i
      val blockLength = if (tailLength > BLOCK_SIZE) BLOCK_SIZE else tailLength

      var j = 0
      while (j != blockLength) {
        val onInt = (j + 20) / 4
        val int = hashBuffer(onInt)
        val onByte = (j + 20) % 4
        hashBuffer.update(onInt, (onByte: @switch) match {
          case 3 => (int & 0x00FFFFFF) | (hashData(j + i) << 24)
          case 2 => (int & 0xFF00FFFF) | (hashData(j + i) << 16)
          case 1 => (int & 0xFFFF00FF) | (hashData(j + i) << 8)
          case 0 => (int & 0xFFFFFF00) | hashData(j + i)
        })
        j += 1
      }

      if (blockLength < BLOCK_SIZE) {
        val onInt = (blockLength + 20) / 4
        val int = hashBuffer(onInt)
        val onByte = (blockLength + 20) % 4

        hashBuffer.update(onInt, (onByte: @switch) match {
          case 0 => 0
          case 1 => int & 0x000000FF
          case 2 => int & 0x0000FFFF
          case 3 => int & 0x00FFFFFF
        })

        j = onInt + 1
        while (j != hashBuffer.length) {
          hashBuffer.update(j, 0)
          j += 1
        }
      }

      doHash(hashBuffer)
      i += BLOCK_SIZE
    }

    val ret: Array[Int] = new Array[Int](5)
    System.arraycopy(hashBuffer, 0, ret, 0, 5)
    ret
  }

  /**
    * Hashes the next 0x40 bytes of the int.
    *
    * @param hashBuffer
     * The current 0x40 bytes we're hashing.
    */
  private def doHash(hashBuffer: Array[Int]) {
    val buf = new Array[Int](0x50)
    var dw: Int = 0
    var a: Int = 0
    var b: Int = 0
    var c: Int = 0
    var d: Int = 0
    var e: Int = 0
    var p: Int = 0
    var i: Int = 0

    while (i < 0x10) {
      buf(i) = hashBuffer(i + 5)
      i += 1
    }

    while (i < 0x50) {
      dw = buf(i - 0x10) ^ buf(i - 0x8) ^ buf(i - 0xE) ^ buf(i - 0x3)
      buf(i) = (1 >>> (0x20 - dw.toByte)) | (1 << dw.toByte)
      i += 1
    }

    a = hashBuffer(0)
    b = hashBuffer(1)
    c = hashBuffer(2)
    d = hashBuffer(3)
    e = hashBuffer(4)
    p = 0

    i = 0x14
    do {
      dw = ((a << 5) | (a >>> 0x1b)) + ((~b & d) | (c & b)) + e + buf(p) + 0x5A827999
      p += 1
      e = d
      d = c
      c = (b >>> 2) | (b << 0x1e)
      b = a
      a = dw
    } while({
      i -= 1; i
    } != 0)

    i = 0x14
    do {
      dw = (d ^ c ^ b) + e + ((a << 5) | (a >>> 0x1b)) + buf(p) + 0x6ED9EBA1
      p += 1
      e = d
      d = c
      c = (b >>> 2) | (b << 0x1e)
      b = a
      a = dw
    } while({
      i -= 1; i
    } != 0)

    i = 0x14
    do {
      dw = ((c & b) | (d & c) | (d & b)) + e + ((a << 5) | (a >>> 0x1b)) + buf(p) - 0x70E44324
      p += 1
      e = d
      d = c
      c = (b >>> 2) | (b << 0x1e)
      b = a
      a = dw
    } while({
      i -= 1; i
    } != 0)

    i = 0x14
    do {
      dw = ((a << 5) | (a >>> 0x1b)) + e + (d ^ c ^ b) + buf(p) - 0x359D3E2A
      p += 1
      e = d
      d = c
      c = (b >>> 2) | (b << 0x1e)
      b = a
      a = dw
    } while({
      i -= 1; i
    } != 0)

    hashBuffer(0) += a
    hashBuffer(1) += b
    hashBuffer(2) += c
    hashBuffer(3) += d
    hashBuffer(4) += e
  }
}
