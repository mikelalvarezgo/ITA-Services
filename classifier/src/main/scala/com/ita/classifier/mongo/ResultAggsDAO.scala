package com.ita.classifier.mongo

import com.ita.classifier.results.ResultsAggs
import com.ita.common.mong.Converters._
import com.ita.common.mong.{DAO, DAOHelpers, MongoDbComponent}
import com.ita.domain.Id
import com.mongodb.casbah.commons.MongoDBObject
import org.bson.types.ObjectId
import com.ita.classifier.Model._

import scala.util.Try
case class ResultsAggslDAO(
  mongoHost: String,
  mongoPort: Int,
  db: String) extends AbsResultsAggsDAO
  with MongoDbComponent
  with DAOHelpers {


  lazy val exec_info = database("aggs")

  override def getAll: Stream[ResultsAggs] = {
    exec_info.find().toStream.map(to[ResultsAggs].apply)
  }

  def create(stateAcc: ResultsAggs): Try[Unit] = Try {
    val bulk = exec_info.initializeOrderedBulkOperation
    bulk.insert(dbObject[ResultsAggs].apply(stateAcc))
    require(bulk.execute().isAcknowledged)
  }

  def update(model_info: ResultsAggs): Try[Unit] =
    for {
      _ <- get(Id(model_info._id.toString))
      _ <- remove(Id(model_info._id.toString))
      _ <- create(model_info)
    } yield {

    }

  def remove(idAccount: Id): Try[Unit] = Try {
    val bulk = exec_info.initializeOrderedBulkOperation
    bulk.find(MongoDBObject("_id" -> idAccount.value)).remove()
    require(bulk.execute().isAcknowledged)
  }
  def getByTopicAndModel(idModel:Id, idTopic:Id) =Try {
    exec_info.findOne(MongoDBObject(
      "topic_id" -> new ObjectId(idTopic.value),
      "model_id" -> new ObjectId(idModel.value))).toStream.map(to[ResultsAggs].apply).head
}

  def get(idAcc: Id): Try[ResultsAggs] = Try {
    exec_info.findOne(MongoDBObject(
      "_id" -> new ObjectId(idAcc.value))).toStream.map(to[ResultsAggs].apply).head
  }

  def get(
    page: Int,
    pageSize: Int,
    sortField: Option[String],
    sortAsc: Option[Boolean]): Try[List[ResultsAggs]] = ???

  def getLatest: Try[ResultsAggs] = Try {
    exec_info.find()
      .sort(MongoDBObject("date" -> -1))
      .map(obj => to[ResultsAggs].apply(obj))
      .toStream.head
  }

  override def latest[U](sorting: ResultsAggs => U)(implicit o: Ordering[U]): Try[ResultsAggs] = ???

}


trait AbsResultsAggsDAO extends DAO[ResultsAggs] {


}
