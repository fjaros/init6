package com.init6

import java.util.concurrent.{Executors, TimeUnit}

import akka.actor.{ActorRef, PoisonPill}
import com.init6.channels.ChannelsActor
import com.init6.connection.{ConnectionHandler, IpLimitActor}
import com.init6.db.{DAO, DAOActor}
import com.init6.servers.{ServerPantyDropper, ServerRegistry}
import com.init6.users.UsersActor

import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.util.Random

/**
 * Created by filip on 9/19/15.
 */
object Init6 extends App with Init6Component {

  DAO
  ServerRegistry()
  DAOActor()
  //ServerPantyDropper(Config().Server.host)
  IpLimitActor(200)
  UsersActor()
  ChannelsActor()

  val random = new Random(System.nanoTime())
  val delay =
    if (random.nextInt(100) < Config().Server.reconThreshold) {
      0
    } else {
      8
    }

  var connectionHandlers: Seq[ActorRef] = _
  val executor = Executors.newSingleThreadScheduledExecutor
  executor.schedule(new Runnable {
    override def run() = {
      connectionHandlers = Config().Server.ports
        .map(port => {
          ConnectionHandler(Config().Server.host, port)
        })
    }
  }, delay, TimeUnit.SECONDS)

  sys.addShutdownHook({
    Option(connectionHandlers).foreach(_.foreach(_ ! PoisonPill))

    implicit val timeout = Duration(10, TimeUnit.SECONDS)
    Await.ready(system.terminate(), timeout)
    DAO.close()
  })
}
