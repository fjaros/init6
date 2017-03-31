package com.init6.coders.commands

import com.init6.Constants._
import com.init6.channels.{User, UserError}

import scala.util.{Random, Try}

/**
  * Created by filip on 3/31/17.
  */
object RollCommand {

  lazy val rollRandom = new Random(System.currentTimeMillis)

  def apply(fromUser: User, message: String): Command = {
    val (min, max) = {
      if (message.nonEmpty) {
        val splt = message.split("-")
        if (splt.length > 2 || (splt.nonEmpty && !splt.forall(_.forall(_.isDigit)))) {
          return UserError(ROLL_FORMAT)
        }

        Try {
          if (splt.length == 1) {
            1 -> splt(0).toInt
          } else {
            splt(0).toInt -> splt(1).toInt
          }
        }.getOrElse(return UserError(ROLL_FORMAT))
      } else {
        1 -> 100
      }
    }

    if (min > max) {
      return UserError(ROLL_FORMAT)
    }

    val roll = rollRandom.nextInt(max - min + 1) + min

    RollCommandToChannel(fromUser, ROLL_INFO(fromUser.name, roll, min, max))
  }
}

case class RollCommandToChannel(override val fromUser: User, override val message: String) extends ChannelBroadcastable
