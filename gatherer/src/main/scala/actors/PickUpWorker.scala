package actors

import akka.actor.Actor
import akka.actor.Actor.Receive
import mongo.daos.PickUpDAO

class PickUpWorker extends  Actor{
  override def receive: Receive = ???
}
