package com.vilenet.coders.binary.hash

import org.scalatest.{Matchers, FlatSpec}

/**
  * Created by filip on 12/13/15.
  */
class BSHA1Spec extends FlatSpec with Matchers {

  "bsha1" should "work" in {

    getStringFromHash(BSHA1("")) should be("67452301efcdab8998badcfe10325476c3d2e1f0")
    getStringFromHash(BSHA1("The quick brown fox jumps over the lazy dog.")) should be("011a2027a5d5c5448356db5e207d391d8d970534")
    getStringFromHash(BSHA1("The quick brown fox jumps over the lazy dog. The quick brown fox jumps over the lazy dog.")) should be("d1cc8c9acaa7eac78ab86f915588114d4d273a3d")
    getStringFromHash(BSHA1(0xDEADBEEF, 0xBADCAB, BSHA1("The quick brown fox jumps over the lazy dog."))) should be("5b4ad103d0c63e0fe6405585c7288e8c6145c1ee")
  }

  def getStringFromHash(hash: Array[Byte]) = {
    hash
      .grouped(4)
      .foldLeft("")((result: String, nextGroup: Array[Byte]) =>
        result + "%02x%02x%02x%02x".format(nextGroup(3), nextGroup(2), nextGroup(1), nextGroup(0))
      )
  }
}
