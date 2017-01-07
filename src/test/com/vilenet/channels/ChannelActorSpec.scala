package com.vilenet.channels

import akka.actor.ActorRef
import akka.testkit.TestActorRef
import com.vilenet.servers.RemoteEvent
import com.vilenet.users.UserUpdated
import com.vilenet.{MockActor, ViLeNetTestComponent}
import org.scalatest._

import scala.collection.mutable

/**
 * Created by filip on 11/6/15.
 */
class ChannelActorSpec extends FunSuite with BeforeAndAfter with Matchers with ViLeNetTestComponent {

  val CHAN_TEST_NAME = "ViLe"

  var channelActor: TestActorRef[ChannelActor]  = _
  var expectedQueue: mutable.SynchronizedQueue[Any] = _
  var actualQueue: mutable.SynchronizedQueue[Any] = _

  before {
    channelActor = TestActorRef[ChannelActor](ChannelActor(CHAN_TEST_NAME))

    expectedQueue = new mutable.SynchronizedQueue[Any]
    actualQueue = new mutable.SynchronizedQueue[Any]
  }

  after {
    //println(s"Expected: $expectedQueue")
    //println(s"  Actual: $actualQueue")
    while (expectedQueue.nonEmpty) {
      actualQueue.dequeue() should be(expectedQueue.dequeue())
    }
    actualQueue.isEmpty should be(true)
  }

  test("AddUser works") {
    val user = User("testUser", channel = CHAN_TEST_NAME)
    val userOp = user.copy(flags = 0x02)

    expectedQueue += UserChannel(userOp, CHAN_TEST_NAME, channelActor)
    expectedQueue += UserIn(userOp)

    val actor = MockActor(actualQueue)

    channelActor ! AddUser(actor, user)
  }

  test("AddUser and RemUser work") {
    val user1 = User("user", channel = CHAN_TEST_NAME)
    val user1Op = user1.copy(flags = 0x02)
    val user2 = User("user2", channel = CHAN_TEST_NAME)
    val user2Op = user2.copy(flags = 0x02)

    expectedQueue += UserChannel(user1Op, CHAN_TEST_NAME, channelActor)
    expectedQueue += UserIn(user1Op)
    expectedQueue += UserJoined(user2)
    expectedQueue += UserChannel(user2, CHAN_TEST_NAME, channelActor)
    expectedQueue += UserIn(user1Op)
    expectedQueue += UserIn(user2)
    expectedQueue += UserLeft(user1Op)
    expectedQueue += UserUpdated(user2Op)
    expectedQueue += UserFlags(user2Op)

    val user1Actor = MockActor(actualQueue)
    val user2Actor = MockActor(actualQueue)

    channelActor ! AddUser(user1Actor, user1)
    channelActor ! AddUser(user2Actor, user2)
    channelActor ! RemUser(user1Actor)
  }


  test("AddUser and Remote AddUser") {
    val user1 = User("user", channel = CHAN_TEST_NAME)
    val user1Op = user1.copy(flags = 0x02)
    val user2 = User("user2", channel = CHAN_TEST_NAME)

    val remoteChannelActor = MockActor(actualQueue)
    val user1Actor = MockActor(actualQueue)
    val user2Actor = MockActor(actualQueue)

    //expectedQueue += RemoteEvent(ChannelUsersLoad(channelActor, channelActor, mutable.Map[ActorRef, User](user1Actor -> user1Op), mutable.Set[ActorRef](user1Actor)))
    expectedQueue += UserChannel(user1Op, CHAN_TEST_NAME, channelActor)
    expectedQueue += UserIn(user1Op)
    expectedQueue += RemoteEvent(AddUser(user1Actor, user1Op))

    channelActor ! ChannelCreated(remoteChannelActor, CHAN_TEST_NAME)
    channelActor ! AddUser(user1Actor, user1)
  }
}
