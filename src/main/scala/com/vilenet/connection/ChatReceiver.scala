package com.vilenet.connection

import akka.actor.ActorRef
import akka.io.Tcp.{Close, Received}
import akka.util.ByteString
import com.vilenet.ViLeNetActor

import scala.annotation.tailrec
import scala.collection.mutable.ArrayBuffer

/**
  * Created by filip on 1/10/16.
  */
trait ChatReceiver extends ViLeNetActor {

  val connection: ActorRef
  val handler: ActorRef

  val buffer = ArrayBuffer[Byte]()


  override def receive: Receive = {
    case Received(data) =>
      @tailrec
      def parsePacket(data: ByteString): Unit = {
        val readData = data.takeWhile(b => b != '\r' && b != '\n')
        if (data.length == readData.length) {
          // Split packet
          buffer ++= readData
        } else {
          // End of packet found
          if (buffer.nonEmpty) {
            handler ! Received(ByteString(buffer.toArray[Byte] ++ readData.toArray[Byte]))
            buffer.clear()
          } else if (readData.nonEmpty) {
            handler ! Received(readData)
          }
        }
        val restOfData = data.drop(readData.length).dropWhile(b => b == '\r' || b == '\n')
        if (restOfData.nonEmpty) {
          parsePacket(restOfData)
        }
      }
      parsePacket(data)
    case x =>
      //println(s"Received $x and closing handler.")
      connection ! Close
  }
}
