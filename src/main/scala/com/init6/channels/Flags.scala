package com.init6.channels

/**
  * Created by filip on 12/3/15.
 *
 * 0x00000001: Blizzard Representative
 * 0x00000002: Channel Operator
 * 0x00000004: Speaker
 * 0x00000008: Battle.net Administrator
 * 0x00000010: No UDP Support
 * 0x00000020: Squelched
 * 0x00000040: Special Guest
 * 0x00000080: This flag has not been seen, however, it is logical to assume that it was once used since it is in the middle of a sequence.
 * 0x00000100: Beep Enabled (Defunct)
 * 0x00000200: PGL Player (Defunct)
 * 0x00000400: PGL Official (Defunct)
 * 0x00000800: KBK Player (Defunct)
 * 0x00001000: WCG Official
 * 0x00002000: KBK Singles (Defunct)
 * 0x00002000: KBK Player (Defunct)
 * 0x00010000: KBK Beginner (Defunct)
 * 0x00020000: White KBK (1 bar) (Defunct)
 * 0x00100000: GF Official
 * 0x00200000: GF Player
 * 0x02000000: PGL Player
 *
 *
  */
object Flags {
  //ADMIN was previously 0x01 but that is Blizz Rep
  val BLIZZ_REP = 0x01
  val OP = 0x02
  val SPEAKER = 0x04
  val ADMIN = 0x08
  val UDP = 0x10
  val SQUELCH = 0x20
  val SPECIAL_GUEST = 0x40
  //0x80?
  val BEEP = 0x100
  val PGL_PLAYER = 0x200
  val PGL_OFFICIAL = 0x400
  val KBK_PLAYER = 0x800
  val WCG_OFFICIAL = 0x1000

  //Added BLIZZ_REP to can BAN
  private val CAN_BAN = BLIZZ_REP | ADMIN | OP
  private val IS_ADMIN = BLIZZ_REP | ADMIN

  private def is(user: User, flag: Int) = (user.flags & flag) == flag
  private def flag(user: User, flag: Int) = user.copy(flags = user.flags | flag)
  private def unflag(user: User, flag: Int) = user.copy(flags = user.flags & ~flag)

  def canBan(user: User) = ((user.flags & CAN_BAN) ^ CAN_BAN) != CAN_BAN
  def canAdmin(user: User) = ((user.flags & IS_ADMIN) ^ IS_ADMIN) != IS_ADMIN

  def op(user: User): User = flag(user, OP)
  def specialGuest(user: User): User = flag(user, SPECIAL_GUEST)
  def deOp(user: User): User = unflag(user, OP)
  def deSpecialGuest(user: User): User = unflag(user, SPECIAL_GUEST)

  def squelch(user: User): User = flag(user, SQUELCH)
  def unsquelch(user: User): User = unflag(user, SQUELCH)

  //Added BLIZZ_REP to isAdmin
  def isAdmin(user: User): Boolean = is(user, ADMIN) || is(user, BLIZZ_REP)
  def isOp(user: User): Boolean = is(user, OP)
  def isSpecialGuest(user: User): Boolean = is(user, SPECIAL_GUEST)
}
