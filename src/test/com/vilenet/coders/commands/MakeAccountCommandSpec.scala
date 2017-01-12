package com.init6.coders.commands

import com.init6.Constants
import com.init6.coders.binary.hash.BSHA1
import org.scalatest.{Matchers, FlatSpec}

/**
  * Created by filip on 12/20/15.
  */
class MakeAccountCommandSpec extends FlatSpec with Matchers {

  "getStringFromHash" should "work" in {
    Constants.getStringFromHash(BSHA1("The quick brown fox jumps over the lazy dog.")) should be ("011a2027a5d5c5448356db5e207d391d8d970534")
  }
}
