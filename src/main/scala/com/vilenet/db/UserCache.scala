package com.vilenet.db

import java.util.concurrent.{TimeUnit, Executors}

import com.vilenet.utils.CaseInsensitiveHashMap

import scala.collection.mutable

/**
  * Created by filip on 12/13/15.
  */
private object UserCache {

  private var cache = CaseInsensitiveHashMap[DbUser]()
  private var inserted = mutable.HashSet[String]()
  private var updated = mutable.HashSet[String]()

  lazy val executorService = Executors.newSingleThreadScheduledExecutor()

  def apply(dbUsers: List[DbUser]) = {
    cache ++= dbUsers.map(dbUser => dbUser.username -> dbUser)

    executorService.scheduleWithFixedDelay(new Runnable {
      override def run() = {
        DAO.saveInserted(cache.filterKeys(inserted.contains).values.toSet)
        DAO.saveUpdated(cache.filterKeys(updated.contains).values.toSet)
        inserted.clear()
        updated.clear()
      }
    }, 10, 10, TimeUnit.SECONDS)
  }

  def close() = {
    executorService.shutdown()
  }

  def get(username: String) = cache.get(username)

  def insert(username: String, passwordHash: Array[Byte]) = {
    synchronized {
      cache += username -> DbUser(username = username, passwordHash = passwordHash)
      inserted += username.toLowerCase
    }
  }

  def update(username: String, dbUser: DbUser) = {
    synchronized {
      get(username).fold()(originalDbUser => {
        if (originalDbUser != dbUser) {
          cache += username -> dbUser
          updated += username.toLowerCase
        }
      })
    }
  }
}
