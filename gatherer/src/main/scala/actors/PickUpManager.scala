package actors

import akka.actor.Actor
import domain.Id
import mongo.daos.PickUpDAO
import ActorMessages._
import utils.{Config, Logger}

class PickUpManager(implicit pickUpDAO: PickUpDAO) extends Actor
  with Config
  with Logger{
  var workers: Map[Id, PickUpWorker] = Map.empty
  var limitWorkers: Int = config.getInt(Config.maxPickUpWorkers)
  override def receive: Receive = {
    case StartPickUp(pickUp) =>
      if(limitWorkers <= workers.size){
        logger.info(s"Cannot start worker for topic $pickUp , limit of workers reached")
        sender() ! AllWorkersBusy
      }else{
        if (workers.find(elem => elem._1 == pickUp._id.get).isDefined) {
          logger.info(s"There is a worker already for this topic ${pickUp._id.get}")
          sender() ! WorkerExists
        } else{
          val worker =
        }

      }

  }
}

object PickUpManager {

}
