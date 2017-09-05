package com.ita.common.mong

import com.mongodb.casbah.MongoClient

trait MongoDbComponent {

  val mongoHost: String
  val mongoPort: Int
  val db: String

  lazy private val mongoClient = MongoClient(mongoHost, mongoPort)
  lazy val database = mongoClient(db)

}
object IDGenerator {
  private var n:Long = 0L

  def next = n+1L
}
object MongoContext {
  type MongoHost = String
  type MongoPort = Int
  type Db = String
  type MongoCtxt = (MongoHost, MongoPort, Db)

}
