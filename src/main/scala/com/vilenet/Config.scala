package com.vilenet

import java.io.File

import com.typesafe.config.ConfigFactory

import scala.collection.JavaConverters._
import scala.util.Try

/**
  * Created by filip on 12/17/15.
  */
object Config {

  def load(filepath: String) = {
    val file = new File(filepath)
    if (file.exists()) {
      ConfigFactory.parseFile(file).resolve()
    } else {
      ConfigFactory.load(filepath)
    }
  }


  val c = load(sys.props("config"))
  val p = c.getConfig(Constants.VILE_NET)

  object Server {

    val p = Config.p.getConfig("server")

    val name = p.getString("name")
    val host = p.getString("host")
    val port = p.getInt("port")

    val akka_nodes = p.getStringList("akka_nodes").asScala
    val akka_host = Try(p.getString("akka_host")).getOrElse(host)
    val akka_port = p.getInt("akka_port")
  }

  object Accounts {

    val p = Config.p.getConfig("accounts")

    val allowedCharacters =
      s"abcdefghijklmnopqrstuvwxyz0123456789[]()${p.getString("allowed-illegal-characters")}".toSet

    val minLength = p.getInt("min-length")
    val maxLength = p.getInt("max-length")

    val enableMultipleLogins = p.getBoolean("enable-multiple")
  }

  object Database {

    val p = Config.p.getConfig("database")

    val host = p.getString("host")
    val port = p.getInt("port")
    val username = p.getString("username")
    val password = p.getString("password")

    val batchUpdateInterval = p.getInt("batch-update-interval")
  }

  val motd = p.getStringList("motd")
    .asScala
    .map(line => {
      line
        .replaceAll("\\$buildNumber", BuildInfo.BUILD_NUMBER)
        .replaceAll("\\$buildHash", BuildInfo.BUILD_HASH)
    })
    .toArray
}
