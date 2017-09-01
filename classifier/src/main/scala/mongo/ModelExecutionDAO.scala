package mongo

import com.mongodb.casbah.commons.MongoDBObject
import domain.Id
import domain.Model._

import scala.util.Try
import mongo._
import mongo.DAOHelpers
import mongo.Converters._
import classifier.Model._
import domain.gatherer.TweetPickUp
import org.bson.types.ObjectId
import results.ModelExecution
case class ModelExecutionlDAO(
  mongoHost: String,
  mongoPort: Int,
  db: String) extends AbsModelExecutionDAO
  with MongoDbComponent
  with DAOHelpers {


  lazy val exec_info = database("executions")

  override def getAll: Stream[ModelExecution] = {
    exec_info.find().toStream.map(to[ModelExecution].apply)
  }

  def create(stateAcc: ModelExecution): Try[Unit] = Try {
    val bulk = exec_info.initializeOrderedBulkOperation
    bulk.insert(dbObject[ModelExecution].apply(stateAcc))
    require(bulk.execute().isAcknowledged)
  }

  def update(model_info: ModelExecution): Try[Unit] =
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

  def get(idAcc: Id): Try[ModelExecution] = Try {
    exec_info.findOne(MongoDBObject(
      "_id" -> new ObjectId(idAcc.value))).toStream.map(to[ModelExecution].apply).head
  }

  def get(
    page: Int,
    pageSize: Int,
    sortField: Option[String],
    sortAsc: Option[Boolean]): Try[List[ModelExecution]] = ???

  def getLatest: Try[ModelExecution] = Try {
    exec_info.find()
      .sort(MongoDBObject("date" -> -1))
      .map(obj => to[ModelExecution].apply(obj))
      .toStream.head
  }

  override def latest[U](sorting: ModelExecution => U)(implicit o: Ordering[U]): Try[ModelExecution] = ???

}


trait AbsModelExecutionDAO extends DAO[ModelExecution] {


}
