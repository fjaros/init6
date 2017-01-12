package com.init6.connection

import akka.util.ByteString
import com.init6.coders.binary.DeBuffer
import org.scalatest.{Matchers, FlatSpec}

/**
  * Created by filip on 12/7/15.
  */
class DeBufferSpec extends FlatSpec with Matchers {

  "debuffer" should "work" in {
    val debuffer = DeBuffer(ByteString(0x1E, 0x00, 0x00, 0x00, 0x00, 0x20, 0x33, 0x34, 0x35, 0x00))

    val dword1e = debuffer.dword()
    val word0020 = debuffer.word()
    val string345 = debuffer.string()

    dword1e should be(0x1E)
    word0020 should be(0x2000)
    string345 should be("345")
  }
}
