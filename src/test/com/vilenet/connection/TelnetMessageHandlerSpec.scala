package com.vilenet.connection

import java.net.InetSocketAddress

import akka.actor.Actor.Receive
import akka.actor.ActorRef
import akka.io.Tcp.Received
import akka.testkit.{TestActorRef, TestActor}
import akka.util.ByteString
import com.vilenet.{ViLeNetActor, ViLeNetTestComponent}
import org.scalatest.FlatSpec

import scala.collection.mutable.ArrayBuffer

/**
 * Created by filip on 11/2/15.
 */


class TelnetMessageHandlerSpec extends FlatSpec {

  "telnet message handler" should "handle split packets" in {

    readTest(ByteString("hello\r\nhow are you"))
    readTest(ByteString("\r\nhello\r\n"))
  }

  var buffer = ArrayBuffer[Byte]()

  def readTest(data: ByteString): ByteString = {
    val readData = data.takeWhile(b => b != '\r' && b != '\n')
    if (data.length == readData.length) {
      // Split packet
      buffer ++= readData
    } else {
      // End of packet found
      if (buffer.nonEmpty) {
        println(s"1 ${ByteString(buffer.toArray[Byte] ++ readData.toArray[Byte])}")
        buffer.clear()
      } else if (readData.nonEmpty) {
        println(s"2 $readData")
      }
    }
    val restOfData = data.drop(readData.length).dropWhile(b => b == '\r' || b == '\n')
    if (restOfData.nonEmpty) {
      readTest(restOfData)
    } else {
      ByteString.empty
    }
  }
}
