package com.ita.gatherer.actors

/**
  * Created by mikelwyred on 20/06/2017.
  */

import akka.stream.actor.ActorPublisher
import com.ita.domain.TweetInfo
import com.ita.domain.utils.{Config, Logger}
import com.ita.gatherer.utils.GathererDataContext

class TweetsPublisher(implicit dataContext:GathererDataContext ) extends ActorPublisher[TweetInfo]
with Config
with Logger{

  override def preStart(): Unit = {
    super.preStart()
    context.system.eventStream.subscribe(self, classOf[TweetInfo] )
  }



  override def receive: Receive = {
  case s: TweetInfo => {
  println(s"tweet recibido: ${s.tweetText}")
    dataContext.tweetsDAO.create(s)
  if (isActive && totalDemand > 0) onNext(s)
}
  case _ =>
    println(s"Nada recibido")

  }

  override def postStop(): Unit = {
  context.system.eventStream.unsubscribe(self)
}

}
object TweetsPublisher {

  def apply()(implicit dataContext: GathererDataContext): TweetsPublisher =
    new TweetsPublisher
}
