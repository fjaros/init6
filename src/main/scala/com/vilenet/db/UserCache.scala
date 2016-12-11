package com.vilenet.db

import java.util.concurrent.{TimeUnit, Executors}

import com.vilenet.Config
import com.vilenet.utils.CaseInsensitiveHashMap

import scala.util.Try

/**
  * Created by filip on 12/13/15.
  */
private object UserCache {

  private val EXISTING = 0
  private val INSERTED = 1
  private val UPDATED = 2

  private val cache = CaseInsensitiveHashMap[(DbUser, Int)]()

  private val executorService = Executors.newSingleThreadScheduledExecutor()

  private val dbUpdateThread = new Runnable {
    override def run() = {
      Try {
        DAO.saveInserted(filterCache(INSERTED))
      }
      Try {
        DAO.saveUpdated(filterCache(UPDATED))
      }
    }

    def filterCache(status: Int) = {
      cache
        .values
        .filter {
          case (_, _status) => _status == status
        }
        .map {
          case (dbUser, _) => dbUser
        }
    }
  }

  def apply(dbUsers: List[DbUser]) = {
    cache ++= dbUsers.map(dbUser => dbUser.username -> (dbUser, EXISTING))

    val updateInterval = Config.Database.batchUpdateInterval
    executorService.scheduleWithFixedDelay(dbUpdateThread, updateInterval, updateInterval, TimeUnit.SECONDS)
  }

  def close() = {
    executorService.shutdown()
    dbUpdateThread.run()
  }

  def get(username: String) = cache.get(username).map { case (dbUser, _) => dbUser }

  def insert(username: String, passwordHash: Array[Byte]) = {
    val newUser = username.toLowerCase
    cache += newUser -> (DbUser(username = newUser, passwordHash = passwordHash), INSERTED)
  }

  def update(username: String, dbUser: DbUser) = {
    get(username).foreach(originalDbUser => {
      if (originalDbUser != dbUser) {
        cache += username -> (dbUser, UPDATED)
      }
    })
  }
}
