package com.ita.common.mong.daos

import com.ita.common.mong.{DAO, DAOHelpers, MongoDbComponent}
import com.ita.domain.{Id, TweetInfo}
import com.mongodb.casbah.commons.MongoDBObject
import com.ita.domain.Model._
import com.ita.common.mong.Converters.{dbObject, to}
import com.mongodb.casbah.commons.MongoDBObject

import scala.util.Try

case class TweetInfoDAO(
  mongoHost: String,
  mongoPort: Int,
  db: String) extends AbsTweetInfoDAO
  with MongoDbComponent
  with DAOHelpers {


  lazy val tweet_info = database("tweets")

  override def getAll: Stream[TweetInfo] = {
    tweet_info.find().toStream.map(to[TweetInfo].apply)
  }

  def create(stateAcc: TweetInfo): Try[Unit] = Try {
    val bulk = tweet_info.initializeOrderedBulkOperation
    bulk.insert(dbObject[TweetInfo].apply(stateAcc))
    require(bulk.execute().isAcknowledged)
  }

  def update(tweet_info: TweetInfo): Try[Unit] =
    for {
      _ <- get(Id(tweet_info.tweet_id.toString))
      _ <- remove(Id(tweet_info.tweet_id.toString))
      _ <- create(tweet_info)
    } yield {

    }

  def remove(idAccount: Id): Try[Unit] = Try {
    val bulk = tweet_info.initializeOrderedBulkOperation
    bulk.find(MongoDBObject("id" -> idAccount.value)).remove()
    require(bulk.execute().isAcknowledged)
  }

  def get(idAcc: Id): Try[TweetInfo] = Try {
    tweet_info.findOne(MongoDBObject(
      "id" -> idAcc.value)).toStream.map(to[TweetInfo].apply).head
  }

  def get(
    page: Int,
    pageSize: Int,
    sortField: Option[String],
    sortAsc: Option[Boolean]): Try[List[TweetInfo]] = ???

  def getLatest: Try[TweetInfo] = Try {
    tweet_info.find()
      .sort(MongoDBObject("date" -> -1))
      .map(obj => to[TweetInfo].apply(obj))
      .toStream.head
  }

  override def latest[U](sorting: TweetInfo => U)(implicit o: Ordering[U]): Try[TweetInfo] = ???

}


trait AbsTweetInfoDAO extends DAO[TweetInfo] {


}

object TweetInfoDAO {

  val IdAccount = "IdCampaign"
  val Amount = "Amount"
  val Date = "Date"

}
