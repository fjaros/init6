package com.init6.db

import com.init6.Config
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
      rs.longOpt(2),
      rs.string(3),
      rs.get[Array[Byte]](4),
      rs.get[Array[Byte]](5),
      rs.boolean(6),
      rs.string(7)
    )
  }

  private[db] def createUser(username: String, passwordHash: Array[Byte]) = UserCache.insert(username, passwordHash)

  private[db] def updateUser(
    username: String,
    account_id: Option[Long] = None,
    password_hash: Option[Array[Byte]] = None,
    flags: Option[Int] = None,
    closed: Option[Boolean] = None,
    closed_reason: Option[String] = None
  ) = {
    getUser(username).foreach(dbUser => {
      UserCache.update(username, dbUser.copy(
        account_id = if (account_id.isDefined) account_id else dbUser.account_id,
        password_hash = password_hash.getOrElse(dbUser.password_hash),
        flags = flags.getOrElse(dbUser.flags),
        closed = closed.getOrElse(dbUser.closed),
        closed_reason = closed_reason.getOrElse(dbUser.closed_reason)
      ))
    })
  }

  def getUser(username: String) = UserCache.get(username)

  private[db] def saveInserted(inserted: Iterable[DbUser]) = {
    if (inserted.nonEmpty) {
      DB localTx { implicit session =>
        withSQL {
          InsertSQLBuilder(sqls"insert ignore into ${DbUser.table}")
            .namedValues(
              DbUser.column.username -> sqls.?,
              DbUser.column.account_id -> sqls.?,
              DbUser.column.password_hash -> sqls.?,
              DbUser.column.flags -> sqls.?
            )
        }.batch(inserted.map(
          dbUser => Seq(
            dbUser.username,
            dbUser.account_id,
            dbUser.password_hash,
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
              DbUser.column.password_hash -> sqls.?,
              DbUser.column.flags -> sqls.?,
              DbUser.column.closed -> sqls.?,
              DbUser.column.closed_reason -> sqls.?
            )
              .where.eq(DbUser.column.column("id"), sqls.?) // applyDynamic does not support passing a vararg parameter?
        }.batch(
          updated.map(dbUser =>
            Seq(
              dbUser.username,
              dbUser.password_hash,
              dbUser.flags,
              dbUser.closed,
              dbUser.closed_reason,
              dbUser.id
            )
          ).toSeq: _*)
            .apply()
      }
    }
  }

  object DbChannelJoin extends SQLSyntaxSupport[DbChannelJoin] {
    override val tableName = "channel_joins"

    def apply(rs: WrappedResultSet) = new DbChannelJoin(
      rs.long(1),
      rs.long(2),
      rs.string(3),
      rs.long(4),
      rs.long(5),
      rs.long(6),
      rs.int(7)
    )
  }

  private[db] def saveChannelJoin(channelJoin: DbChannelJoin) = {
    DB localTx { implicit session =>
      withSQL {
        insertInto(DbChannelJoin)
          .values(
            None,
            channelJoin.user_id,
            channelJoin.channel,
            channelJoin.server_accepting_time,
            channelJoin.channel_created_time,
            channelJoin.joined_time,
            channelJoin.joined_place
          )
      }
      .update()
      .apply()
    }
  }
}
