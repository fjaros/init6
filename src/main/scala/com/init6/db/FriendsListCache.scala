package com.init6.db

import scala.collection.mutable

/**
  * Created by filip on 2/9/17.
  */
private[db] class FriendsListCache(dbFriendsList: Seq[DbFriend]) {

  private val cache = (mutable.HashMap.newBuilder ++= dbFriendsList.groupBy(_.user_id)).result()

  def get(id: Long) = cache.get(id)

  def insert(user_id: Long, friend_position: Int, friend_id: Long, friend_name: String): DbFriend = {
    val dbFriend = DbFriend(user_id = user_id, friend_position = friend_position, friend_id = friend_id, friend_name = friend_name)

    cache += cache
      .get(user_id)
      .fold(user_id -> Seq(dbFriend))(seq => user_id -> (seq :+ dbFriend))
    DAO.saveInsertedFriend(dbFriend)

    dbFriend
  }

  def update(user_id: Long, friend_position: Int, friend_id: Long, friend_name: String): DbFriend = {
    val dbFriend = DbFriend(user_id = user_id, friend_position = friend_position, friend_id = friend_id, friend_name = friend_name)

    cache
      .get(user_id)
      .foreach(seq => user_id -> (seq :+ dbFriend))
    DAO.saveUpdatedFriend(dbFriend)

    dbFriend
  }

  def delete(user_id: Long, friend_position: Int): DbFriend = {
    val dbFriend = cache(user_id)(friend_position - 1)

    cache
      .get(user_id)
      .foreach(seq => {
        cache += user_id -> ((0 until friend_position - 1).map(seq) ++
        (friend_position until seq.length).map(i => {
          val newDbFriend = seq(i).copy(friend_position = i)
          DAO.saveUpdatedFriend(newDbFriend)
          newDbFriend
        }))
        DAO.saveDeletedFriend(dbFriend)
      })

    dbFriend
  }
}
