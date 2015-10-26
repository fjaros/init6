package com.vilenet.coders.binary.packets

import akka.util.ByteString
import com.vilenet.coders.binary.BinaryPacket

/**
 * Created by filip on 10/25/15.
 */
object SidAuthInfo extends BinaryPacket {

  def apply(): ByteString = {
    build(ID_SID_AUTH_INFO,
      ByteString.newBuilder
        .putInt(0)
        .putInt(0xdeadbeef)
        .putInt(0xdeadbeef)
        .putInt(0x4341AC00)
        .putInt(0x01C50B25)
        .putBytes("IX86ver3.mpq")
        .putBytes("A=125933019 B=665814511 C=736475113 4 A=A+S B=B^C C=C^A A=A^B")
        .result()
    )
  }
}
