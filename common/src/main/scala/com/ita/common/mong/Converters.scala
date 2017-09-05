package com.ita.common.mong

import com.mongodb.DBObject
import com.mongodb.util.JSON
import spray.json._
import spray.json.DefaultJsonProtocol._
object Converters {

  implicit def toMongoDBObject[I: JsonFormat, O]: I => O = i =>
    JSON.parse(implicitly[JsonFormat[I]].write(i).prettyPrint).asInstanceOf[O]

  implicit def dbObject[T: JsonFormat]: T => DBObject =
    toMongoDBObject[T, DBObject].apply

  implicit def to[T: JsonFormat]: DBObject => T = dbo =>
    implicitly[JsonFormat[T]].read(JsonParser(dbo.toString))

  def fun1(b: Array[Array[Int]]): Int = {
    val l = b.length
    val bb = b.foldLeft[(Int, Int)]((0, 0))(
      (accu: (Int, Int), c: Array[Int]) => (accu._1 + c(accu._2) + c(l - accu._2),
        accu._2 + 1))
    bb._1
  }

}

