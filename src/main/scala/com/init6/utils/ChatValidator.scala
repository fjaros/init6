package com.init6.utils

import akka.util.ByteString

/**
  * Created by filip on 1/26/17.
  */
object ChatValidator {

  def apply(data: ByteString) = {
    data.forall(b => {
      b < 0 || b >= 32
    })
  }
}
