package com.vilenet.db

import java.sql.{DriverManager, Connection}

import akka.actor.{Props, ActorRef, ActorSystem, Actor}

import scalikejdbc._

/**
 * Created by filip on 9/20/15.
 */
object DAO {

  Class.forName("org.mariadb.jdbc.Driver")
  ConnectionPool.singleton("jdbc:mariadb://localhost:3306/vilenet", "vile", "12345")
  implicit val session = AutoSession

  case class User(id: Long, username: String, password: String, flags: Long)

  implicit def decodeFlags(flags: Array[Byte]): Long = flags(0) << 24 | flags(1) << 16 | flags(2) << 8 | flags(3)

  object User extends SQLSyntaxSupport[User] {
    override val tableName = "users"
    def apply(rs: WrappedResultSet) = new User(rs.long(1), rs.string(2), rs.string(3), rs.get[Array[Byte]](4))
  }

  val u = User.syntax("u")
  val findUser: String => Option[User] = (username: String) => {
    withSQL {
      select.from(User as u).where.eq(u.username, username)
    }.map(rs => User(rs)).single().apply()
  }
}
