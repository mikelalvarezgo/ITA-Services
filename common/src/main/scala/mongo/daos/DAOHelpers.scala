package mongo.daos

import com.mongodb.util.JSON
import domain.Id
import org.bson.types.ObjectId
import spray.json.JsonFormat
import domain.Model.JFid
/**
  * Helper functions for DAOs
  */
trait DAOHelpers {


  implicit def toMongoDBObject[I: JsonFormat, O]: I => O = i =>
    JSON.parse(implicitly[JsonFormat[I]].write(i).prettyPrint).asInstanceOf[O]

  def objectId(id: Id): ObjectId =
    toMongoDBObject[Id,ObjectId].apply(id)

  /**
    * Generates a new MongoDB ObjectId
    * wrapped into some domain Id object.
    */
  def genObjectId(): Id =
    ObjectId.get().toHexString

}
