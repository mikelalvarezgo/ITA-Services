package com.ita.gatherer.actors

import ActorMessages.{StartPickUp, StopPickUp}
import akka.actor.{Actor, ActorSystem}
import akka.actor.Actor.Receive
import com.ita.common.mong.daos.PickUpDAO
import com.ita.domain.TweetInfo
import com.ita.domain.utils.{Config, Logger}
import com.ita.gatherer.restApi.controllers.TwitterStreamClient
import com.ita.gatherer.utils.{DAOS, GathererDataContext}
import com.ita.gatherer.utils.DAOS.tweetInfoDao

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

      client = new TwitterStreamClient(context.system,topic)
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
