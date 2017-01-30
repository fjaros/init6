package com.init6.db

/**
  * Created by filip on 12/13/15.
  */
case class DbUser(
  id: Long = 0,
  account_id: Option[Long] = None,
  username: String,
  password_hash: Array[Byte],
  flags: Int = 0,
  closed: Boolean = false,
  closed_reason: String = ""
)


case class DbChannelJoin(
  id: Long = 0,
  user_id: Long,
  channel: String,
  server_accepting_time: Long,
  channel_created_time: Long,
  joined_time: Long,
  joined_place: Int
)