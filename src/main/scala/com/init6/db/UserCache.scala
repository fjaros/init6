package com.init6.db

import java.util.concurrent.{Executors, TimeUnit}

import com.init6.Config
import com.init6.utils.CaseInsensitiveHashMap

import scala.collection.mutable
import scala.util.Try

/**
  * Created by filip on 12/13/15.
  */
private[db] class UserCache(dbUsers: List[DbUser]) {

  private val cache = CaseInsensitiveHashMap[DbUser]()
  private val inserted = mutable.HashSet[String]()
  private val updated = mutable.HashSet[String]()

  private val executorService = Executors.newSingleThreadScheduledExecutor()
  private val updateInterval = Config().Database.batchUpdateInterval

  private val dbUpdateThread = new Runnable {
    override def run() = {
      Try {
        DAO.saveInserted(cache.filterKeys(inserted.contains).values.toSet)
        inserted.clear()
      }
      Try {
        DAO.saveUpdated(cache.filterKeys(updated.contains).values.toSet)
        updated.clear()
      }
    }
  }

  cache ++= dbUsers.map(dbUser => dbUser.username -> dbUser)
  executorService.scheduleWithFixedDelay(dbUpdateThread, updateInterval, updateInterval, TimeUnit.SECONDS)

  def close() = {
    executorService.shutdown()
    dbUpdateThread.run()
  }

  def get(username: String) = cache.get(username)

  def insert(username: String, password_hash: Array[Byte]) = {
    val now = System.currentTimeMillis
    val newUser = username.toLowerCase
    cache += newUser -> DbUser(username = newUser, password_hash = password_hash,
      created = now, last_logged_in = now)
    inserted += username.toLowerCase
  }

  def update(username: String, dbUser: DbUser) = {
    get(username).foreach(originalDbUser => {
      if (originalDbUser != dbUser) {
        cache += username -> dbUser
        updated += username.toLowerCase
      }
    })
  }
}
