package com.vilenet

import java.io.File

import com.typesafe.config.ConfigFactory

import scala.collection.JavaConverters._


/**
  * Created by filip on 12/17/15.
  */
object Config {

  def load(filepath: String) = {
//    val file = new File(filepath)
//    if (file.exists()) {
//      ConfigFactory.parseFile(file).resolve()
//    } else {
        ConfigFactory.load(filepath)
//    }
  }


  val c = load("vilenet.conf")
  val p = c.getConfig(Constants.VILE_NET)

  object Accounts {

    val p = Config.p.getConfig("accounts")

    val allowedCharacters =
      s"abcdefghijklmnopqrstuvwxyz0123456789${p.getString("allowed-illegal-characters")}".toSet

    val minLength = p.getInt("min-length")
    val maxLength = p.getInt("max-length")
  }

  object Database {

    val p = Config.p.getConfig("database")

    val host = p.getString("host")
    val port = p.getInt("port")
    val username = p.getString("username")
    val password = p.getString("password")

    val batchUpdateInterval = p.getInt("batch-update-interval")
  }

  val motd = p.getStringList("motd").asScala.toArray
}
