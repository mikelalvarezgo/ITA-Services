package com.ita.gatherer.actors


import akka.actor.{Actor, ActorRef, ActorSystem, PoisonPill, Props}
import ActorMessages._
import com.ita.common.mong.daos.PickUpDAO
import com.ita.domain.Id
import com.ita.domain.utils.{Config, Logger}
import com.ita.gatherer.utils.GathererDataContext

class PickUpManager(implicit dataContext:GathererDataContext ,system: ActorSystem) extends Actor
  with Config
  with Logger{
  import PickUpManager._
  var workers: Map[Id, ActorRef] = Map.empty
  var limitWorkers: Int = config.getInt(Config.maxPickUpWorkers)
  override def preStart() = {
   // get pickups in process and create actors, send message to actor for start
  }
  override def receive: Receive = {
    case CollectPickUp(pickUp) =>
      logger.info(s"${~>} CURRENT WORKERS -> ${workers.toString()}")
      logger.info(s"${~>} Received message for collect pickup ${pickUp._id.get}")
      val worker = workers.find(elem => elem._1 == pickUp._id.get).get._2
      worker ! StartPickUp(pickUp)
      logger.info(s"${~>} Forwarded message for collect pickup to worker $worker" )
      sender() ! WorkerStarted
    case StartPickUp(pickUp) =>
      logger.info(s"${~>} Received message for start worker pickup ${pickUp._id.get}")
      if(limitWorkers <= workers.size){
        logger.info(s"${~>}Cannot start worker for topic $pickUp , limit of workers reached")
        sender() ! AllWorkersBusy
      }else{
        if (workers.find(elem => elem._1 == pickUp._id.get).isDefined) {
          logger.info(s"${~>}There is a worker already for this topic ${pickUp._id.get}")
          sender() ! WorkerExists
        } else{
          val newWorker:ActorRef = context.actorOf(Props(PickUpWorker()), s"Worker-${pickUp._id.get.value}")
          workers = workers.+( pickUp._id.get -> newWorker)
          newWorker !  StartPickUp(pickUp)
          sender() ! WorkerStarted
          logger.info(s"${~>}Added worker for  $pickUp ")
        }
      }
    case StopPickUp(pickUp) =>
      if (workers.find(elem => elem._1 == pickUp._id.get).isDefined) {
        workers(pickUp._id.get) ! PoisonPill
        logger.info(s"${~>}Stopped worker for topic ${pickUp._id.get}")
        workers = workers.-(pickUp._id.get)
        sender() ! WorkerStopped


      }else{
        logger.info(s"${~>}Trying to stop worker that does not exist ${pickUp._id.get}")
        sender() ! WorkerNotExists
      }
  }
}

object PickUpManager {
  val ~> = "[PICK-UP MANAGER] "
  def apply()(implicit ndataContext: GathererDataContext,system: ActorSystem): PickUpManager =
    new PickUpManager()
}
