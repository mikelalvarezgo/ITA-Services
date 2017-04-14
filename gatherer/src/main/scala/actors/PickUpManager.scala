package actors

import akka.actor.Actor
import domain.Id
import mongo.daos.PickUpDAO

class PickUpManager(implicit pickUpDAO: PickUpDAO) extends Actor{
  override def receive: Receive = ???
}
