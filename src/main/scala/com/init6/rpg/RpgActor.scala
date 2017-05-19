package com.init6.rpg

import java.util.concurrent.TimeUnit

import akka.actor.Props
import com.init6.Constants._
import com.init6.{Init6Actor, Init6Component}
import com.init6.channels.{ChatEvent, UserError, UserInfo, UserInfoArray}
import com.init6.coders.commands.Command
import com.init6.db.DAO
import com.init6.db.DAO.DbRpg
import com.init6.utils.CaseInsensitiveHashMap

import scala.collection.mutable
import scala.concurrent.duration.Duration
import scala.util.Random

/**
  * Created by filip on 5/13/17.
  */
trait RpgCommand extends Command
case class RpgPlay(userId: Long, username: String) extends RpgCommand
case class RpgStatus(userId: Long) extends RpgCommand
case class RpgDuel(userId: Long, who: String) extends RpgCommand
case class RpgTravel(userId: Long, where: String) extends RpgCommand
case class RpgParty(userId: Long, who: String) extends RpgCommand

object RpgActor extends Init6Component {
  def apply() = system.actorOf(Props[RpgActor], INIT6_RPG_PATH)
}

class RpgActor extends Init6Actor {

  val random = new Random(System.currentTimeMillis)
  val lock = new Object

  val areas = Map(
    // Cities
    0 -> "Gillysickle",
    1 -> "Trundlemeed",
    2 -> "Ryeswald",
    3 -> "Waldengrol",

    // Areas
    4 -> "Kaldoran Shores",
    5 -> "Briar's Basin",
    6 -> "Lairsgrind",
    7 -> "Forest of Refuge",
    8 -> "Netherwind",
    9 -> "Kaldoran Tundra",
    10 -> "Dreadsands",
    11 -> "Meylogthrol",
    12 -> "Voiden Sands"
  )

  val distances = Map(
    (0, 1) -> 14,
    (0, 2) -> 23,
    (0, 3) -> 7,
    (0, 4) -> 22,
    (0, 5) -> 19,
    (0, 6) -> 12,
    (0, 7) -> 21,
    (0, 8) -> 21,
    (0, 9) -> 24,
    (0, 10) -> 8,
    (0, 11) -> 7,
    (0, 12) -> 9,
    (1, 2) -> 11,
    (1, 3) -> 5,
    (1, 4) -> 8,
    (1, 5) -> 7,
    (1, 6) -> 17,
    (1, 7) -> 12,
    (1, 8) -> 11,
    (1, 9) -> 5,
    (1, 10) -> 24,
    (1, 11) -> 21,
    (1, 12) -> 24,
    (2, 3) -> 9,
    (2, 4) -> 22,
    (2, 5) -> 10,
    (2, 6) -> 20,
    (2, 7) -> 18,
    (2, 8) -> 17,
    (2, 9) -> 9,
    (2, 10) -> 5,
    (2, 11) -> 8,
    (2, 12) -> 19,
    (3, 4) -> 5,
    (3, 5) -> 15,
    (3, 6) -> 15,
    (3, 7) -> 10,
    (3, 8) -> 19,
    (3, 9) -> 19,
    (3, 10) -> 20,
    (3, 11) -> 18,
    (3, 12) -> 13,
    (4, 5) -> 22,
    (4, 6) -> 11,
    (4, 7) -> 18,
    (4, 8) -> 24,
    (4, 9) -> 12,
    (4, 10) -> 11,
    (4, 11) -> 24,
    (4, 12) -> 24,
    (5, 6) -> 13,
    (5, 7) -> 7,
    (5, 8) -> 20,
    (5, 9) -> 16,
    (5, 10) -> 10,
    (5, 11) -> 11,
    (5, 12) -> 17,
    (6, 7) -> 5,
    (6, 8) -> 21,
    (6, 9) -> 10,
    (6, 10) -> 7,
    (6, 11) -> 11,
    (6, 12) -> 23,
    (7, 8) -> 7,
    (7, 9) -> 21,
    (7, 10) -> 9,
    (7, 11) -> 25,
    (7, 12) -> 17,
    (8, 9) -> 9,
    (8, 10) -> 6,
    (8, 11) -> 7,
    (8, 12) -> 21,
    (9, 10) -> 14,
    (9, 11) -> 15,
    (9, 12) -> 18,
    (10, 11) -> 19,
    (10, 12) -> 24,
    (11, 12) -> 13
  )

  val items = Map(
    0 -> "a Plain Old Cudgel (+0 Attack/+0 Defense)",
    1 -> "iKoN's Extremely Tight Pants (-1 Attack/+2 Defense)",
    2 -> "AlenL's Left Testicle (+3 Attack/-1 Defense)",
    3 -> "SiDz's Assortment of Farm Animals (+2 Attack/+1 Defense",
    4 -> "Poseidon's Belly Button Ashtray (-1 Attack/+3 Defense)",
    5 -> "Zero's Linux VPS Command (+0 Attack/-2 Defense)"
  )

  val players = mutable.Map.empty[Long, DbRpg]
  val namesToIds = CaseInsensitiveHashMap[Long]()
  val travelCommands = mutable.Map.empty[Long, Int]

  override def preStart() = {
    super.preStart()

    val getAll = DAO.rpgGetAll()
    players ++= getAll.map(rpg => {
      rpg.user_id -> rpg
    })
    namesToIds ++= getAll.map(rpg => {
      rpg.username -> rpg.user_id
    })

    import context.dispatcher
    system.scheduler.schedule(
      Duration(0, TimeUnit.MINUTES),
      Duration(1, TimeUnit.MINUTES)
    )({
      players.toSeq.foreach {
        case (userId, rpg) =>
          var (nExp, nLevel) =
            if (rpg.from_area <= 3) {
              (rpg.experience, rpg.level)
            } else {
              if (rpg.experience + 5 >= getExperienceForNextLevel(rpg.level)) {
                (rpg.experience + 5 - getExperienceForNextLevel(rpg.level), rpg.level + 1)
              } else {
                (rpg.experience + 5, rpg.level)
              }
            }

          if (travelCommands.contains(userId)) {
            nExp -= getDistance(rpg.from_area, travelCommands(userId)) * 10
          }

          val (nHp) =
            if (rpg.from_area <= 3) {
              if (rpg.hp < getHpFromLevel(nLevel)) {
                math.min(rpg.hp + random.nextInt(10) + 5, getHpFromLevel(nLevel))
              } else {
                rpg.hp
              }
            } else {
              if (rpg.hp > 5) {
                math.max(5, rpg.hp - random.nextInt(15) - 5)
              } else {
                rpg.hp
              }
            }

          val (fromArea, newMovement) =
            if (travelCommands.contains(userId)) {
              (rpg.to_area, getNewMovement(rpg.to_area))
            } else {
              if (System.currentTimeMillis >= rpg.action_end_time) {
                (rpg.to_area, getNewMovement(rpg.to_area))
              } else {
                (rpg.from_area, (rpg.to_area, rpg.action_start_time, rpg.action_end_time))
              }
            }

          val item =
            if (random.nextInt(100) < 5) {
              random.nextInt(5) + 1
            } else {
              rpg.item
            }


          val newDbRpg = rpg.copy(
            level = nLevel,
            experience = nExp,
            hp = nHp,
            from_area = fromArea,
            to_area = newMovement._1,
            action_start_time = newMovement._2,
            action_end_time = newMovement._3,
            item = rpg.item
          )

          DAO.rpgUpdatePlayer(newDbRpg)
          players.put(userId, newDbRpg)
          travelCommands -= userId
      }
    })
  }

  override def receive = {
    case RpgPlay(userId: Long, username: String) =>
      players.get(userId).fold[Unit]({
        val area = random.nextInt(10) + 3
        val newMovement = getNewMovement(area)
        val dbRpg = DbRpg(userId, username, 1, 0, 100, 1, newMovement._2, newMovement._3, area, newMovement._1, 0)
        DAO.rpgRegisterNew(dbRpg)
        players.put(userId, dbRpg)
        namesToIds += username -> userId
        sender() ! UserInfo("Registered! Check /rpg for your character progress.")
      })(x => sender() ! getStatus(userId))
    case RpgStatus(userId: Long) =>
      sender() ! getStatus(userId)
    case RpgTravel(userId: Long, where: String) =>
      players.get(userId).fold[Unit]({
        sender() ! UserError("You are not playing the rpg.")
      })(rpg => {
        getIdFromZoneName(where).fold(sender() ! UserError("This zone is invalid."))(zoneInfo => {
          travelCommands.put(userId, zoneInfo._1)
          if (rpg.from_area == zoneInfo._1) {
            sender() ! UserInfo("You are already in " + rpg.from_area + ", but you will stay here longer.")
          } else {
            val expCost = getDistance(rpg.from_area, zoneInfo._1) * 10
            if (rpg.experience - expCost < 0) {
              sender() ! UserError("Traveling to " + zoneInfo._1 + " costs " + expCost + ". You don't have enough!")
            } else {
              sender() ! UserInfo("You will soon travel to " + zoneInfo._2 + ". It will cost you " + expCost + " experience.")
            }
          }
        })
      })
  }

  def getIdFromZoneName(where: String) = {
    val whereLower = where.toLowerCase
    areas.find {
      case (_, zone) => zone.toLowerCase == whereLower
    }
  }

  def getStatus(userId: Long) = {
    players.get(userId).fold[ChatEvent](UserError("You are not playing the rpg."))(rpg => {
      val heading = Duration(rpg.action_end_time - System.currentTimeMillis, TimeUnit.MILLISECONDS).toMinutes

      UserInfoArray(Array(
        "You are " + rpg.username,
        "Level " + rpg.level,
        "Experience " + rpg.experience + " / " + getExperienceForNextLevel(rpg.level),
        "Hit points " + rpg.hp + " / " + getHpFromLevel(rpg.level),
        "Wielding " + items(rpg.item),
        "Currently " + { if (rpg.from_area <= 3) "resting" else "questing"} + " in " +
          areas(rpg.from_area) + { if (rpg.from_area <= 3) " (City)" else "" } + ".",
        "Heading to " + areas(rpg.to_area) +
          { if (rpg.to_area <= 3) " (City)" else "" } + " to " +
          { if (rpg.to_area <= 3) "rest" else "quest" } + " in about " + heading + " minutes."
      ))
    })
  }

  def getHpFromLevel(level: Int) = {
    math.round((1 until level).foldLeft(100.toDouble) { case (result, _) => result * 1.07 }).toInt
  }

  def getExperienceForNextLevel(level: Int) = {
    math.round((1 until level).foldLeft(1000.toDouble) { case (result, _) => result * 1.14 }).toInt
  }

  def getDistance(fromArea: Int, toArea: Int) = {
    if (toArea > fromArea) {
      distances(fromArea -> toArea)
    } else {
      distances(toArea -> fromArea)
    }
  }

  def getNewMovement(area: Int): (Int, Long, Long) = {
    var newArea = area
    do {
      newArea =
        if (area > 3) {
          random.nextInt(13)
        } else {
          random.nextInt(10) + 3
        }
    } while (
      newArea == area
    )

    val timeForAction = getDistance(area, newArea) - 5 + random.nextInt(11)

    val currentMillis = System.currentTimeMillis

    (newArea, currentMillis, currentMillis + timeForAction * 1000 * 60)
  }
}

