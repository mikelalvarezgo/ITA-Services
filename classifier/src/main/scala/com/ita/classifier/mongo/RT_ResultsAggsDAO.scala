
package com.ita.classifier.mongo

import com.ita.classifier.results.{ RT_ResultsAggs}
import com.ita.common.mong.Converters._
import com.ita.common.mong.{DAO, DAOHelpers, MongoDbComponent}
import com.ita.domain.Id
import com.mongodb.casbah.commons.MongoDBObject
import org.bson.types.ObjectId
import com.ita.classifier.Model._

import scala.util.Try
case class RT_RT_ResultsAggsDAO(
  mongoHost: String,
  mongoPort: Int,
  db: String) extends AbsRT_ResultsAggsDAO
  with MongoDbComponent
  with DAOHelpers {


  lazy val exec_info = database("rt_aggs")

  override def getAll: Stream[RT_ResultsAggs] = {
    exec_info.find().toStream.map(to[RT_ResultsAggs].apply)
  }

  def create(stateAcc: RT_ResultsAggs): Try[Unit] = Try {
    val bulk = exec_info.initializeOrderedBulkOperation
    bulk.insert(dbObject[RT_ResultsAggs].apply(stateAcc))
    require(bulk.execute().isAcknowledged)
  }

  def update(model_info: RT_ResultsAggs): Try[Unit] =
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
      "model_id" -> new ObjectId(idModel.value))).toStream.map(to[RT_ResultsAggs].apply).head
  }

  def get(idAcc: Id): Try[RT_ResultsAggs] = Try {
    exec_info.findOne(MongoDBObject(
      "_id" -> new ObjectId(idAcc.value))).toStream.map(to[RT_ResultsAggs].apply).head
  }

  def get(
    page: Int,
    pageSize: Int,
    sortField: Option[String],
    sortAsc: Option[Boolean]): Try[List[RT_ResultsAggs]] = ???

  def getLatest: Try[RT_ResultsAggs] = Try {
    exec_info.find()
      .sort(MongoDBObject("date" -> -1))
      .map(obj => to[RT_ResultsAggs].apply(obj))
      .toStream.head
  }

  override def latest[U](sorting: RT_ResultsAggs => U)(implicit o: Ordering[U]): Try[RT_ResultsAggs] = ???

}


trait AbsRT_ResultsAggsDAO extends DAO[RT_ResultsAggs] {


}
