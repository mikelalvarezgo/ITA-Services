package com.ita.classifier.mongo

import com.ita.common.mong.Converters._
import com.ita.common.mong.{DAO, DAOHelpers, MongoDbComponent}
import com.ita.domain.Id
import com.mongodb.casbah.commons.MongoDBObject
import org.bson.types.ObjectId
import com.ita.classifier.models._
import scala.util.Try
import com.ita.classifier.Model._

case class ModelDAO(
  mongoHost: String,
  mongoPort: Int,
  db: String) extends AbsModelDataDAO
  with MongoDbComponent
  with DAOHelpers {


  lazy val model_info = database("models")

  override def getAll: Stream[ModelData] = {
    model_info.find().toStream.map(to[ModelData].apply)
  }

  def create(stateAcc: ModelData): Try[Unit] = Try {
    val bulk = model_info.initializeOrderedBulkOperation
    bulk.insert(dbObject[ModelData].apply(stateAcc))
    require(bulk.execute().isAcknowledged)
  }

  def update(model_info: ModelData): Try[Unit] =
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

  def get(idAcc: Id): Try[ModelData] = Try {
    model_info.findOne(MongoDBObject(
      "_id" -> new ObjectId(idAcc.value))).toStream.map(to[ModelData].apply).head
  }

  def get(
    page: Int,
    pageSize: Int,
    sortField: Option[String],
    sortAsc: Option[Boolean]): Try[List[ModelData]] = ???

  def getLatest: Try[ModelData] = Try {
    model_info.find()
      .sort(MongoDBObject("date" -> -1))
      .map(obj => to[ModelData].apply(obj))
      .toStream.head
  }

  override def latest[U](sorting: ModelData => U)(implicit o: Ordering[U]): Try[ModelData] = ???

}


trait AbsModelDataDAO extends DAO[ModelData] {


}
