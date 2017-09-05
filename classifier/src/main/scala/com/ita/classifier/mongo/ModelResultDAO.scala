package com.ita.classifier.mongo

import com.ita.classifier.results.ModelResult
import com.ita.common.mong.Converters._
import com.ita.common.mong.{DAO, DAOHelpers, MongoDbComponent}
import com.ita.domain.Id
import com.mongodb.casbah.commons.MongoDBObject
import org.bson.types.ObjectId
import com.ita.classifier.Model._
import scala.util.Try
case class ModelResultDAO(
  mongoHost: String,
  mongoPort: Int,
  db: String) extends AbsModelResultDAO
  with MongoDbComponent
  with DAOHelpers {


  lazy val model_info = database("model_results")

  override def getAll: Stream[ModelResult] = {
    model_info.find().toStream.map(to[ModelResult].apply)
  }

  def create(stateAcc: ModelResult): Try[Unit] = Try {
    val bulk = model_info.initializeOrderedBulkOperation
    bulk.insert(dbObject[ModelResult].apply(stateAcc))
    require(bulk.execute().isAcknowledged)
  }

  def update(model_info: ModelResult): Try[Unit] =
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

  def get(idAcc: Id): Try[ModelResult] = Try {
    model_info.findOne(MongoDBObject(
      "_id" -> new ObjectId(idAcc.value))).toStream.map(to[ModelResult].apply).head
  }

  def get(
    page: Int,
    pageSize: Int,
    sortField: Option[String],
    sortAsc: Option[Boolean]): Try[List[ModelResult]] = ???

  def getLatest: Try[ModelResult] = Try {
    model_info.find()
      .sort(MongoDBObject("date" -> -1))
      .map(obj => to[ModelResult].apply(obj))
      .toStream.head
  }

  override def latest[U](sorting: ModelResult => U)(implicit o: Ordering[U]): Try[ModelResult] = ???

}


trait AbsModelResultDAO extends DAO[ModelResult] {


}