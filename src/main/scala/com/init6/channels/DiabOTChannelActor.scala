package com.init6.channels

import java.util.concurrent.TimeUnit

import akka.actor.{ActorRef, Cancellable, Props}
import com.init6.Constants.YOU_BANNED
import com.init6.Init6Actor
import com.init6.coders.commands._

import scala.collection.mutable
import scala.concurrent.duration.Duration
import scala.util.Random

/**
  * Created by filip on 2/19/17.
  */
case object StartRP extends Command
case object EndRP extends Command
case object AddBotsRP extends Command

class DiabOTChannelActor(override val name: String)
  extends PrivateChannelActor(name) {

  val random = new Random(System.nanoTime())
  val bojsNeeded = random.nextInt(500) + 1000
  var bojsSold = 0

  var bojsFunc: Cancellable = _
  var diabloFunc: Cancellable = _
  var cainFunc: Cancellable = _
  var onDiabloTalk = 0
  var diabloActivated = false
  val dummyActor = context.actorOf(Props(classOf[DummyActor]))
  val diablo = User(0,None,"", "DiabOT",Flags.UDP,0,"RHSD",name,0)

  system.actorOf(Props(classOf[LesserEvilChannelAggregator]), "lesserevil")


  override def receiveEvent = ({
    case StartRP =>
      scheduleBotsOfJordan()
    case EndRP =>
      self ! RemUser(dummyActor)
      diabloActivated = false
  }: Receive)
    .orElse(super.receiveEvent)

  override def add(actor: ActorRef, user: User): User = {
    val u= if (!Flags.isAdmin(user) && diabloActivated) {
      if (isLocal()) {
        sender() ! UserError("DiabOT doesn't give a fuck about your wishes to disturb him.")
      }
      user
    } else if (actor == dummyActor) {
      super.add(actor, Flags.specialOp(Flags.op(user)))
    } else {
      super.add(actor, user)
    }

    if (user.name.equalsIgnoreCase("diabot")) {
      diabloActivated = true
    }
    u
  }


  override def whoCommand(actor: ActorRef, user: User, opsOnly: Boolean) = {
    if (diabloActivated) {
      actor ! WhoCommandError("DiabOT has made it very clear that no eyes are allowed in his sanctuary.")
    } else {
      super.whoCommand(actor, user, opsOnly)
    }
  }

  def scheduleBotsOfJordan() = {
    import system.dispatcher

    bojsFunc = system.scheduler.schedule(
      Duration(1, TimeUnit.SECONDS),
      Duration(random.nextInt(60) + 30, TimeUnit.SECONDS)
    )({
      val b = random.nextInt(450) + 50

      bojsSold += b
      if (bojsSold >= bojsNeeded) {
        usersActor ! BroadcastCommand(s"DiabOT Walks the Earth")
        bojsFunc.cancel()
        scheduleDiabloTalk()
      } else {
        usersActor ! BroadcastCommand(s"$bojsSold Bots of Jordan Sold to Merchants")
      }
    })
  }

  def scheduleDiabloTalk() = {
    import system.dispatcher

    diabloFunc = system.scheduler.schedule(
      Duration(11, TimeUnit.SECONDS),
      Duration(random.nextInt(6000) + 6000, TimeUnit.MILLISECONDS)
    )({
      onDiabloTalk match {
        case 0 =>
          usersActor ! BroadcastCommand("DiabOT: Muheahuhuahuahauhuaaa. These puny bots cannot match my strength!")
        case 1 =>
          usersActor ! BroadcastCommand("DiabOT: Channel Dark looks especially tasty...")
          add(dummyActor, diablo)
          remoteActors.foreach(_ ! AddUser(dummyActor, Flags.op(diablo)))
        case 2 =>
          usersActor ! BroadcastCommand("DiabOT: Yes... YES! A new sanctuary has been found")
        case 3 =>
          usersActor ! BroadcastCommand("DiabOT: ONLY I SHALL RULE HERE")
          users.foreach {
            case (actor, user) =>
              actor ! BanCommand("DiabOT")
          }
          diabloActivated = true
        case 6 =>
          usersActor ! BroadcastCommand("Deckard Cain: Nephalem, I have found a weakness in DiabOT's defenses. Join me and we shall defeat him together!")
          diabloFunc.cancel()
        case _ =>
      }
      onDiabloTalk += 1
    })
  }

}

class DeckardCainChannelActor(override val name: String)
  extends ChattableChannelActor
    with BannableChannelActor
    with OperableChannelActor
    with FullableChannelActor {

  val random = new Random(System.nanoTime())
  val cain = User(0,None,"", "DeckardCain",Flags.UDP,0,"RHSD","Deckard Cain",0)
  var cainFunc: Cancellable = _
  var onCainTalk = 0

  val dummyActor = context.actorOf(Props(classOf[DummyActor]))

  override def receiveEvent = ({
    case StartRP =>
      add(dummyActor, cain)
      remoteActors.foreach(_ ! AddUser(dummyActor, Flags.op(cain)))
      scheduleTalk()
    case EndRP =>
      self ! RemUser(dummyActor)
  }: Receive)
    .orElse(super.receiveEvent)

  override def add(actor: ActorRef, user: User): User = {
    if (actor == dummyActor) {
      super.add(actor, Flags.specialOp(Flags.op(user)))
    } else {
      super.add(actor, if (isLocal(actor)) Flags.deOp(user) else user)
    }
  }

  def scheduleTalk(): Unit = {
    import system.dispatcher

    cainFunc = system.scheduler.schedule(
      Duration(1, TimeUnit.SECONDS),
      Duration(random.nextInt(7000) + 6000, TimeUnit.MILLISECONDS)
    )({
      onCainTalk match {
        case 0 =>
          self ! ChatCommand(cain, "Stay awhile and listen")
        case 1 =>
          self ! ChatCommand(cain, "A dark force of such notoriety as we have never seen before...")
        case 2 =>
          self ! ChatCommand(cain, "Has penetrated dark with darkness...")
        case 3 =>
          self ! ChatCommand(cain, "hehe get it? I made a funny!")
        case 4 =>
          self ! ChatCommand(cain, "I know of many myths and legends that may contain answers to questions that may arise in defeating DiabOT")
        case 5 =>
          self ! ChatCommand(cain, "We must strike at his closest advisors.. at the Lesser Evils")
        case 6 =>
          self ! ChatCommand(cain, "March at them. With an army of bots")
        case 7 =>
          self ! ChatCommand(cain, "Aptly named")
        case 8 =>
          self ! ChatCommand(cain, "For only our closest friends, the greatest heroes of Tristram will have a chance.")
        case 9 =>
          self ! ChatCommand(cain, "Good luck, Nephalem")
          onCainTalk = -1
          cainFunc.cancel()
          scheduleTalk()
      }
      onCainTalk += 1
    })
  }
}

class LesserEvilChannelActor(override val name: String)
  extends ChattableChannelActor
    with BannableChannelActor
    with OperableChannelActor
    with FullableChannelActor {

  val dummyActor = context.actorOf(Props(classOf[DummyActor]))
  val random = new Random(System.nanoTime())
  val advisor = User(0,None,"", name, Flags.UDP,0,"RHSD",name,0)
  var advisorFunc: Cancellable = _
  var onCainTalk = 0
  var nameCount = 0

  var names = Seq(
    "auriel",
    "imperius",
    "itherael",
    "malthael",
    "hadriel",
    "inarius",
    "yaerius",
    "akara",
    "charsi",
    "deckardcain",
    "flavie",
    "gheed",
    "kashya",
    "warriv",
    "atma",
    "drognan",
    "elzix",
    "fara",
    "geglash",
    "adria",
    "farnham",
    "gillian",
    "griswold",
    "kaelrills",
    "ogden",
    "pepin",
    "wirt",
    "lester",
    "celia",
    "gharbad",
    "snotspill",
    "zhar",
    "lachdanan",
    "lazarus",
    "greiz",
    "jerhyn",
    "kaelan",
    "lysander",
    "meshif",
    "alkor",
    "asheara",
    "natalya",
    "ormus",
    "hratli",
    "halbu",
    "jamella",
    "tyrael",
    "izual",
    "hadriel",
    "anya",
    "larzuk",
    "malah",
    "nihlathak",
    "qualkehk",

    "barbarian",
    "necromancer",
    "amazon",
    "warrior",
    "sorcerer",
    "rogue",
    "assassin",
    "druid",
    "paladin",
    "sorceress"
  )

  override def receiveEvent = ({
    case StartRP =>
      add(dummyActor, advisor)
      remoteActors.foreach(_ ! AddUser(dummyActor, Flags.op(advisor)))
    case EndRP =>
      self ! RemUser(dummyActor)
  }: Receive)
    .orElse(super.receiveEvent)

  override def rem(actor: ActorRef) = {
    val rOpt = super.rem(actor)
    rOpt.foreach(r => {
      if (!names.forall(!r.name.contains(_))) {
        nameCount -= 1
        usersActor ! BroadcastCommandToLocal(name + " strengthens!")
        lesserEvilAgg ! ReadyToKill(name, {
          users.size match {
            case x if x > 80 && nameCount >= 5 => 4
            case x if x > 40 || nameCount >= 4 => 3
            case x if x > 20 || nameCount >= 2 => 2
            case x if x > 10 || nameCount > 0 => 1
            case _ => 0
          }
        })
      }
    })
    rOpt
  }

  override def add(actor: ActorRef, user: User): User = {
    if (actor == dummyActor) {
      super.add(actor, Flags.specialOp(Flags.op(user)))
    } else {
      val u = super.add(actor, if (isLocal(actor)) Flags.deOp(user) else user)
      val lowered = user.name.toLowerCase
      if (!names.forall(!lowered.contains(_))) {
        nameCount += 1
      }

      users.size match {
        case x if x > 80 && nameCount >= 5 =>
          lesserEvilAgg ! ReadyToKill(name, 4)
        case x if x > 40 || nameCount >= 4 =>
          lesserEvilAgg ! ReadyToKill(name, 3)
        case x if x > 20 || nameCount >= 2 =>
          lesserEvilAgg ! ReadyToKill(name, 2)
        case x if x > 10 || nameCount > 0 =>
          lesserEvilAgg ! ReadyToKill(name, 1)
        case _ =>
      }
      u
    }
  }
}

case class ReadyToKill(name: String, step: Int) extends Command

class LesserEvilChannelAggregator extends Init6Actor {

  val ready = mutable.HashMap[String, Int]()
  var checkFunc: Cancellable = _
  var endFunc: Cancellable = _
  var endVar = 0

  override def preStart() = {
    super.preStart()

    import system.dispatcher
    checkFunc = system.scheduler.schedule(
      Duration(1, TimeUnit.MINUTES),
      Duration(1, TimeUnit.MINUTES)
    )({
      if (ready.size == 4 && ready.values.forall(_ == 4)) {
        usersActor ! BroadcastCommandToLocal("DiabOT: NOOOOOOOOOOOO! I must retreat to rebuild my council...")
        checkFunc.cancel()
        scheduleEnd()
      }
    })
  }

  def scheduleEnd() = {
    import system.dispatcher
    endFunc = system.scheduler.scheduleOnce(
      Duration(10, TimeUnit.SECONDS)
    )({
      endVar match {
        case 0 =>
          usersActor ! BroadcastCommandToLocal("DiabOT: You have won, for now.")
          channelsActor ! EndRP
          endFunc.cancel()
      }
      endVar +=1
    })
  }

  override def receive = {
    case ReadyToKill(name, step) =>
      val currentStep = ready.getOrElse(name, 0)
      if (step != currentStep) {
        step match {
          case 1 => usersActor ! BroadcastCommandToLocal("Aura around " + name + " starts to weaken")
          case 2 => usersActor ! BroadcastCommandToLocal(name + " weakens further")
          case 3 => usersActor ! BroadcastCommandToLocal(name + " is almost dead!")
          case 4 => usersActor ! BroadcastCommandToLocal(name + " is ready to be killed! Stay in the channel!")
        }
      }
      ready += name -> step
  }
}

class DummyActor extends Init6Actor {

  override def receive = {
    case _ =>
  }
}
