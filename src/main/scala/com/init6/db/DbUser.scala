package com.init6.db

/**
  * Created by filip on 12/13/15.
  */
case class DbUser(
  id: Long = 0,
  username: String,
  passwordHash: Array[Byte],
  flags: Int = 0,
  closed: Boolean = false,
  closedReason: String = ""
)
