package com.init6.coders.binary.packets

import akka.util.ByteString
import com.init6.coders.binary.{BinaryPacket, DeBuffer}

import scala.util.Try

object SidReadUserData extends BinaryPacket {

  case class SidReadUserData(numAccounts: Int, numKeys: Int, requestId: Int, accounts: Array[String], keys: Array[String])

  override val PACKET_ID = Packets.SID_READUSERDATA

  def apply(numAccounts: Int, numKeys: Int, requestId: Int, keys: Array[String]) = {
    val bs = ByteString.newBuilder
      .putInt(numAccounts)
      .putInt(numKeys)
      .putInt(requestId)
    keys.foreach(key => bs.putBytes(key))
    build(bs.result())
  }

  def unapply(data: ByteString): Option[SidReadUserData] = {
    Try {
      val debuffer = DeBuffer(data)
      val numAccounts = debuffer.dword()
      val numKeys = debuffer.dword()
      val requestId = debuffer.dword()
      val accounts = (0 until numAccounts).map(_ => debuffer.string()).toArray
      val keys = (0 until numKeys).map(_ => debuffer.string()).toArray
      SidReadUserData(numAccounts, numKeys, requestId, accounts, keys)
    }.toOption
  }
}
