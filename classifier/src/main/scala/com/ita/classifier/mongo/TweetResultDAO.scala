package com.ita.classifier.mongo

import com.mongodb.casbah.commons.MongoDBObject
import com.ita.common.mong.Converters._
import com.ita.classifier.results.ModelExecution
import com.ita.classifier.results.TweetResult
import com.ita.common.mong.{DAO, DAOHelpers, MongoDbComponent}
import com.ita.domain.Id
import com.ita.classifier.models.ModelData
import org.bson.types.ObjectId
import com.ita.classifier.Model._

import scala.util.Try
case class TweetResultDAO(
  mongoHost: String,
  mongoPort: Int,
  db: String) extends AbsTweetResultDAO
  with MongoDbComponent
  with DAOHelpers {


  lazy val model_info = database("tweet_results")

  override def getAll: Stream[TweetResult] = {
    model_info.find().toStream.map(to[TweetResult].apply)
  }

  def create(stateAcc: TweetResult): Try[Unit] = Try {
    val bulk = model_info.initializeOrderedBulkOperation
    bulk.insert(dbObject[TweetResult].apply(stateAcc))
    require(bulk.execute().isAcknowledged)
  }

  def update(model_info: TweetResult): Try[Unit] =
    for {
      _ <- get(Id(model_info._id.toString))
      _ <- remove(Id(model_info._id.toString))
      _ <- create(model_info)
    } yield {

    }

  def remove(idAccount: Id): Try[Unit] = Try {
    val bulk = model_info.initializeOrderedBulkOperation
    bulk.find(MongoDBObject("_id" -> idAccount.value)).remove()
    require(bulk.execute().isAcknowledged)
  }

  def get(idAcc: Id): Try[TweetResult] = Try {
    model_info.findOne(MongoDBObject(
      "_id" -> new ObjectId(idAcc.value))).toStream.map(to[TweetResult].apply).head
  }

  def get(
    page: Int,
    pageSize: Int,
    sortField: Option[String],
    sortAsc: Option[Boolean]): Try[List[TweetResult]] = ???

  def getLatest: Try[TweetResult] = Try {
    model_info.find()
      .sort(MongoDBObject("date" -> -1))
      .map(obj => to[TweetResult].apply(obj))
      .toStream.head
  }

  override def latest[U](sorting: TweetResult => U)(implicit o: Ordering[U]): Try[TweetResult] = ???

}


trait AbsTweetResultDAO extends DAO[TweetResult] {


}