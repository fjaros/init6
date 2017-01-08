package com.vilenet.db

import com.vilenet.Config
import scalikejdbc._

/**
 * Created by filip on 9/20/15.
 */
object DAO {

  Class.forName("org.mariadb.jdbc.Driver")
  ConnectionPool.singleton(
    s"jdbc:mariadb://${Config().Database.host}:${Config().Database.port}/vilenet", Config().Database.username, Config().Database.password
  )
  implicit val session = AutoSession

  val usersCache = UserCache(withSQL {
    select.from(DbUser as DbUser.syntax("user"))
  }.map(rs => DbUser(rs)).list().apply())

  implicit def decodeFlags(flags: Array[Byte]): Int = flags(0) << 24 | flags(1) << 16 | flags(2) << 8 | flags(3)

  def close() = {
    UserCache.close()
    session.close()
  }

  object DbUser extends SQLSyntaxSupport[DbUser] {
    override val tableName = "users"

    def apply(rs: WrappedResultSet) = new DbUser(
      rs.long(1),
      rs.string(2),
      rs.get[Array[Byte]](3),
      rs.get[Array[Byte]](4),
      rs.boolean(5),
      rs.string(6)
    )
  }

  private[db] def createUser(username: String, passwordHash: Array[Byte]) = UserCache.insert(username, passwordHash)

  private[db] def updateUser(
    username: String,
    passwordHash: Array[Byte] = Array[Byte](),
    flags: Int = -1,
    closed: Option[Boolean] = None,
    closedReason: Option[String] = None
  ) = {
    getUser(username).foreach(dbUser => {
      UserCache.update(username, dbUser.copy(
        passwordHash = if (passwordHash.nonEmpty) passwordHash else dbUser.passwordHash,
        flags = if (flags != -1) flags else dbUser.flags,
        closed = closed.getOrElse(dbUser.closed),
        closedReason = closedReason.getOrElse(dbUser.closedReason)
      ))
    })
  }

  def getUser(username: String) = UserCache.get(username)

  private[db] def saveInserted(inserted: Iterable[DbUser]) = {
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

  private[db] def saveUpdated(updated: Iterable[DbUser]) = {
    if (updated.nonEmpty) {
      DB localTx { implicit session =>
        withSQL {
          update(DbUser)
            .set(
              DbUser.column.username -> sqls.?,
              DbUser.column.passwordHash -> sqls.?,
              DbUser.column.flags -> sqls.?,
              DbUser.column.closed -> sqls.?,
              DbUser.column.closedReason -> sqls.?
            )
              .where.eq(DbUser.column.column("id"), sqls.?) // applyDynamic does not support passing a vararg parameter?
        }.batch(
          updated.map(dbUser =>
            Seq(
              dbUser.username,
              dbUser.passwordHash,
              dbUser.flags,
              dbUser.closed,
              dbUser.closedReason,
              dbUser.id
            )
          ).toSeq: _*)
            .apply()
      }
    }
  }
}
