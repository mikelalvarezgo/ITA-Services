package actors

import actors.ActorMessages.{StartPickUp, StopPickUp}
import akka.actor.{Actor, ActorSystem}
import akka.actor.Actor.Receive
import domain.TweetInfo
import domain.gatherer.TweetPickUp
import mongo.daos.PickUpDAO
import utils.DAOS.tweetInfoDao
import utils._

class PickUpWorker(implicit dataContext:GathererDataContext, system:ActorSystem) extends  Actor
  with Config
  with Logger
  with DAOS
{
  import PickUpWorker.~>
  var client:TwitterStreamClient = _
  override def receive: Receive ={
    case StartPickUp(topic) =>
      logger.info(s"${~>} RECEIVED START PICKUP")

      client = new TwitterStreamClient(system,topic)
      client.init

    case StopPickUp(pickUp) =>
      client.stop

    case _ => logger.info(s"${~>} RECEIVED SOMETHING")
  }
}
object  PickUpWorker {
  val ~> = "[PICK-UP WORKER] "

  def apply()(implicit dataContext: GathererDataContext,system: ActorSystem): PickUpWorker =
    new PickUpWorker

}
