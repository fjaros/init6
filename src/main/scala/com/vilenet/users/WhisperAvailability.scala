package com.vilenet.users

import akka.actor.ActorRef
import com.vilenet.Constants._
import com.vilenet.channels.UserInfo

/**
  * Created by filip on 1/14/16.
  */
trait WhisperAvailability {

  private var isEnabled = false
  private var message = UNAVAILABLE_DEFAULT_MSG

  protected val username: String

  protected val enableMessageFunc: String
  protected val cancelMessageFunc: String
  protected val whisperMessageFunc: (String, String) => String

  def enableAction(message: String)(implicit sender: ActorRef) = {
    if (message.nonEmpty) {
      sender ! UserInfo(enableMessageFunc)
      isEnabled = true
      this.message = message
    } else {
      sender ! UserInfo(if (isEnabled) cancelMessageFunc else enableMessageFunc)
      isEnabled = !isEnabled
      this.message = UNAVAILABLE_DEFAULT_MSG
    }
    isEnabled
  }

  def whisperAction(senderActor: ActorRef): Option[Boolean] = {
    if (isEnabled) {
      senderActor ! UserInfo(whisperMessageFunc(username, message))
      Some(isEnabled)
    } else {
      None
    }
  }
}

object AwayAvailablity {
  def apply(username: String) = new AwayAvailablity(username)
}

sealed class AwayAvailablity(override val username: String) extends WhisperAvailability {

  override val enableMessageFunc = AWAY_ENGAGED
  override val cancelMessageFunc = AWAY_CANCELLED
  override val whisperMessageFunc = AWAY_UNAVAILABLE
}

object DndAvailablity {
  def apply(username: String) = new DndAvailablity(username)
}

sealed class DndAvailablity(override val username: String) extends WhisperAvailability {

  override val enableMessageFunc = DND_ENGAGED
  override val cancelMessageFunc = DND_CANCELLED
  override val whisperMessageFunc = DND_UNAVAILABLE
}
