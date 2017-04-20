package com.init6.users

import java.util.concurrent.TimeUnit

import akka.actor.Props
import com.init6.Constants._
import com.init6.channels.{ChatEvent, UserError, UserInfoArray}
import com.init6.coders.commands.Command
import com.init6.db.DAO
import com.init6.db.DAO.Ranking
import com.init6.{Config, Init6Actor, Init6Component}

import scala.collection.mutable.ArrayBuffer
import scala.concurrent.duration.Duration

/**
  * Created by filip on 4/17/17.
  */
object RankingActor extends Init6Component {
  def apply() = system.actorOf(Props[RankingActor], INIT6_RANKING_PATH)
}

case class GetRankingCommand(serverIp: String, channel: String) extends Command

class RankingActor extends Init6Actor {

  private var rankings: Option[Map[String, ArrayBuffer[Ranking]]] = None

  override def preStart() = {
    super.preStart()

    import context.dispatcher
    system.scheduler.scheduleOnce(
      Duration(30, TimeUnit.SECONDS)
    )({
      rankings = Some(DAO.getRankings.foldLeft(Map.empty[String, ArrayBuffer[Ranking]]) {
        case (result, ranking) =>
          result
            .get(ranking.channel)
            .fold({
              val channelRankings = new ArrayBuffer[Ranking](20)
              channelRankings += ranking
              result + (ranking.channel -> channelRankings)
            })(channelRankings => {
              if (channelRankings.length < 20) {
                channelRankings += ranking
              }
              result
            })
      })
    })
  }

  override def receive = {
    case GetRankingCommand(serverIp, channel) =>
      sender() ! rankings.fold[ChatEvent](UserError(RANKINGS_NOT_YET_READY))(rankings => {
        rankings
          .get(channel.toLowerCase)
          .fold[ChatEvent](UserError(RANKINGS_NOT_AVAILABLE(channel)))(rankings => {
            UserInfoArray(
              RANKINGS_HEADER(channel, Config().Server.host) ++
              rankings.zipWithIndex.map {
                case (ranking, rank) => RANKING_FORMAT(rank + 1, ranking)
              }
            )
          })
      })
  }
}
