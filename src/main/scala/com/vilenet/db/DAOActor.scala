package com.vilenet.db

import akka.actor.Props
import com.vilenet.Constants._
import com.vilenet.coders.commands.Command
import com.vilenet.{ViLeNetActor, Config, ViLeNetComponent}
import scalikejdbc._

/**
 * Created by filip on 9/20/15.
 */
object DAOActor extends ViLeNetComponent {
  def apply() = system.actorOf(Props(new DAOActor), VILE_NET_DAO_PATH)
}

case class AccountCreated(username: String, passwordHash: Array[Byte]) extends Command
case class AccountUpdated(username: String, passwordHash: Array[Byte]) extends Command

class DAOActor extends ViLeNetActor {

  Class.forName("org.mariadb.jdbc.Driver")
  ConnectionPool.singleton(
    s"jdbc:mariadb://${Config.Database.host}:${Config.Database.port}/vilenet", Config.Database.username, Config.Database.password
  )
  implicit val session = AutoSession

  val usersCache = UserCache(withSQL {
    select.from(DbUser as DbUser.syntax("user"))
  }.map(rs => DbUser(rs)).list().apply())

  implicit def decodeFlags(flags: Array[Byte]): Int = flags(0) << 24 | flags(1) << 16 | flags(2) << 8 | flags(3)

  override def receive: Receive = {
    case _ =>
  }

  def close() = {
    UserCache.close()
    session.close()
  }

  object DbUser extends SQLSyntaxSupport[DbUser] {
    override val tableName = "users"

    def apply(rs: WrappedResultSet) = new DbUser(rs.long(1), rs.string(2), rs.get[Array[Byte]](3), rs.get[Array[Byte]](4))
  }

  def createUser(username: String, passwordHash: Array[Byte]) = UserCache.insert(username, passwordHash)
  def updateUser(username: String, password: String = "", passwordHash: Array[Byte] = Array[Byte](), flags: Int = -1) = {
    getUser(username).fold()(dbUser => {
      UserCache.update(username, dbUser.copy(
        passwordHash = if (passwordHash.nonEmpty) passwordHash else dbUser.passwordHash,
        flags = if (flags != -1) flags else dbUser.flags
      ))
    })
  }

  def getUser(username: String) = UserCache.get(username)

  private[db] def saveInserted(inserted: Set[DbUser]) = {
    if (inserted.nonEmpty) {
      DB localTx { implicit session =>
        withSQL {
          insertInto(DbUser)
            .namedValues(
              DbUser.column.username -> sqls.?,
              DbUser.column.passwordHash -> sqls.?,
              DbUser.column.flags -> sqls.?
            )
        }.batch(inserted.map(
          dbUser => Seq(
            dbUser.username,
            dbUser.passwordHash,
            dbUser.flags
          )
        ).toSeq: _*)
          .apply()
      }
    }
  }

  private[db] def saveUpdated(updated: Set[DbUser]) = {
    if (updated.nonEmpty) {
      DB localTx { implicit session =>
        withSQL {
          update(DbUser)
            .set(
              DbUser.column.username -> sqls.?,
              DbUser.column.passwordHash -> sqls.?,
              DbUser.column.flags -> sqls.?
            )
              .where.eq(DbUser.column.column("id"), sqls.?) // applyDynamic does not support passing a vararg parameter?
        }.batch(
          updated.map(dbUser =>
            Seq(
              dbUser.username,
              dbUser.passwordHash,
              dbUser.flags,
              dbUser.id
            )
          ).toSeq: _*)
            .apply()
      }
    }
  }
}
