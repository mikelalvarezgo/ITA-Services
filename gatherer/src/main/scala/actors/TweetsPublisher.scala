package actors

/**
  * Created by mikelwyred on 20/06/2017.
  */

import akka.stream.actor.ActorPublisher
import domain.TweetInfo
import utils.{Config, GathererDataContext, Logger}

class TweetsPublisher(implicit dataContext:GathererDataContext ) extends ActorPublisher[TweetInfo]
with Config
with Logger{

  val sub = context.system.eventStream.subscribe(self, classOf[TweetInfo])

  override def receive: Receive = {
  case s: TweetInfo => {
  println(s"Trino recibido: ${s.tweetText}")
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
