package com.vilenet.coders.binary.hash

import org.scalatest.{Matchers, FlatSpec}

import scala.collection.mutable.ArrayBuffer

/**
  * Created by filip on 12/13/15.
  */
class BSHA1Spec extends FlatSpec with Matchers {

  "bsha1" should "work" in {

    getStringFromHash(BSHA1("")) should be("67452301efcdab8998badcfe10325476c3d2e1f0")
    getStringFromHash(BSHA1("The quick brown fox jumped over the lazy dog.")) should be("b42d0f2578a404a7296b803b1e61e44a866b583b")
    getStringFromHash(BSHA1("The quick brown fox jumped over the lazy dog. The quick brown fox jumped over the lazy dog.")) should be("754c851813303dfac6fec37c84b517069788fcd5")
    getStringFromHash(BSHA1(0xDEADBEEF, 0xBADCAB, "The quick brown fox jumped over the lazy dog.".getBytes)) should be("604bc0f968ba575a994e6772c300325006260937")
  }

  def getStringFromHash(hash: Array[Byte]) = {
    val ret = StringBuilder.newBuilder
    for (i <- hash.indices by 4) {
      ret ++= "%02x%02x%02x%02x".format(hash(i + 3), hash(i + 2), hash(i + 1), hash(i))
    }
    ret.result()
  }
}
