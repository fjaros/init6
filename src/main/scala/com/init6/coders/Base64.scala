package com.init6.coders

/**
  * Created by filip on 1/4/17.
  */
object Base64 {
  def apply(str: String) = new String(java.util.Base64.getUrlEncoder.encode(str.getBytes))
}
