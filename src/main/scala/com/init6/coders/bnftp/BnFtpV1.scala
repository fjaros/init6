package com.init6.coders.bnftp

import akka.util.ByteString
import com.init6.coders.binary.DeBuffer

import java.io.File
import scala.util.Try

object BnFtpV1 extends BnFtpPacket {

  def apply(fileStartPosition: Int, file: File): ByteString = {
    build(
      ByteString.newBuilder
        .putShort(0)
        .putInt(file.length().toInt)
        .putInt(0)
        .putInt(0)
        .putLong(file.lastModified())
        .putBytes(file.getName)
        .result(),
      fileStartPosition, file
    )
  }

  def unapply(data: ByteString): Option[BnFtpV1] = {
    Try {
      val debuffer = DeBuffer(data)
      debuffer.skip(16) // platform id, product id, ad banner id, ad banner file extension
      val fileStartPosition = debuffer.dword()
      debuffer.skip(8) // file time
      val fileName = debuffer.string()
      BnFtpV1(fileStartPosition, fileName)
    }.toOption
  }
}

case class BnFtpV1(fileStartPosition: Int, fileName: String)
