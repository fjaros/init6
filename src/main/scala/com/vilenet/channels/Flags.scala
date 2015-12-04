package com.vilenet.channels

/**
  * Created by filip on 12/3/15.
  */
object Flags {
  val ADMIN = 0x01
  val OP = 0x02
  val SQUELCH = 0x20

  private val CAN_BAN = ADMIN | OP

  private def is(user: User, flag: Int) = (user.flags & flag) == flag
  private def flag(user: User, flag: Int) = user.copy(flags = user.flags | flag)
  private def unflag(user: User, flag: Int) = user.copy(flags = user.flags & ~flag)

  def canBan(user: User) = ((user.flags & CAN_BAN) ^ CAN_BAN) != CAN_BAN

  def op(user: User): User = flag(user, OP)
  def deOp(user: User): User = unflag(user, OP)

  def squelch(user: User): User = flag(user, SQUELCH)
  def unsquelch(user: User): User = unflag(user, SQUELCH)

  def isAdmin(user: User): Boolean = is(user, ADMIN)
  def isOp(user: User): Boolean = is(user, OP)
}
