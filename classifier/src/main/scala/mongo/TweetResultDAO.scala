package mongo

import com.mongodb.casbah.commons.MongoDBObject
import domain.Id
import mongo._
import mongo.DAOHelpers
import mongo.Converters._
import results.ModelExecution
import results.TweetResult

import classifier.Model._
import scala.util.Try
case class TweetResultDAO(
  mongoHost: String,
  mongoPort: Int,
  db: String) extends AbsTweetResultDAO
  with MongoDbComponent
  with DAOHelpers {


  lazy val model_info = database("models")

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
    bulk.find(MongoDBObject("id" -> idAccount.value)).remove()
    require(bulk.execute().isAcknowledged)
  }

  def get(idAcc: Id): Try[TweetResult] = Try {
    model_info.findOne(MongoDBObject(
      "id" -> idAcc.value)).toStream.map(to[TweetResult].apply).head
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