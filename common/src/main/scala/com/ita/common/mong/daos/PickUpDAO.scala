package com.ita.common.mong.daos

import com.ita.domain.Id
import com.mongodb.casbah.commons.MongoDBObject
import com.ita.common.mong.Converters.{dbObject, to}
import com.ita.common.mong.Converters._
import com.ita.common.mong.{DAO, DAOHelpers, MongoDbComponent}
import com.ita.domain.gatherer.TweetPickUp
import com.mongodb.casbah.Imports._
import com.mongodb.casbah.query.dsl.BSONType.BSONObjectId
import org.bson.types.ObjectId
import com.ita.common.mong.Converters.{dbObject, to}
import com.ita.domain.Model._
import com.ita.domain.gatherer.Model._
import scala.util.Try

case class PickUpDAO(mongo_Host: String, mongo_Port: Int, db_name: String) extends AbsPickUpDAO
  with MongoDbComponent
  with DAOHelpers {

  import PickUpDAO._

  override val mongoHost: String = mongo_Host
  override val mongoPort: Int = mongo_Port
  override val db: String = db_name

  lazy val pickups = database("pickups")

  override def getAll: Stream[TweetPickUp] =
    pickups.find().toStream.map(to[TweetPickUp].apply)

  override def create(t: TweetPickUp): Try[Unit] = Try {

    val bulk = pickups.initializeOrderedBulkOperation
    bulk.insert(dbObject[TweetPickUp].apply(t))
    require(bulk.execute().isAcknowledged)
  }

  override def update(t: TweetPickUp): Try[Unit] = {
    for {
      _ <- get(t._id.get)
      _ <- remove(t._id.get)
      _ <- create(t)
    } yield {}
  }

  override def get(id: Id): Try[TweetPickUp] = Try {
    pickups.findOne(MongoDBObject(
      idPickUp -> new ObjectId(id.value))).toStream.map(to[TweetPickUp].apply).head
  }

  def find(query: MongoDBObject): Try[TweetPickUp] = Try {
    pickups.find(query).toStream.map(dbo => to[TweetPickUp].apply(dbo)).head
  }

  def findAll(query: MongoDBObject): Try[List[TweetPickUp]] = Try {
    pickups.find(query).toStream.map(dbo => to[TweetPickUp].apply(dbo)).toList
  }

  override def remove(id: Id): Try[Unit] = Try {
    val bulk = pickups.initializeOrderedBulkOperation
    bulk.find(MongoDBObject(idPickUp -> new ObjectId(id.value))).remove()
    require(bulk.execute().isAcknowledged)
  }

  /**
    * Paginated 'get'
    */
  override def get(page: Int, pageSize: Int, sortField: Option[String], sortAsc: Option[Boolean]): Try[List[TweetPickUp]] = ???

  /**
    * Get latest element of T type that was created.
    */
  override def latest[U](sorting: (TweetPickUp) => U)(implicit o: Ordering[U]): Try[TweetPickUp] = ???
}

trait AbsPickUpDAO extends DAO[TweetPickUp] {


}

object PickUpDAO {

  val idPickUp = "_id"

}
