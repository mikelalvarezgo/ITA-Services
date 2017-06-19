package actors

import actors.ActorMessages.StartPickUp
import akka.actor.Actor
import akka.actor.Actor.Receive
import domain.TweetInfo
import domain.gatherer.TweetPickUp
import mongo.daos.PickUpDAO
import utils.DAOS.tweetInfoDao
import utils._

class PickUpWorker(implicit dataContext:GathererDataContext) extends  Actor
  with Config
  with Logger
  with TwitterCredentials
  with SparkLoad
  with DAOS
{
  import PickUpWorker.~>
  override def receive: Receive ={

    case StartPickUp(topic) =>{
      //  Add filters ..
      val filtersConf =  topic.topics
      filter(
        filtersConf
      )
      val idiomsFilter = config.getStringList("twitter.lenguages").asInstanceOf[List[String]]
          .map{ leng => TweetsFilter(s"filter_$leng",Some(leng))}

      //  ... add actions to perform ...
      when { tweets  =>
        tweets foreach ( tweet => {
          idiomsFilter foreach(filter =>
            if(filter.isTweetLenguage(tweet.getText)) {
              {
                val tweetInfo = TweetInfo.content2TwitterInfo(tweet, filter.lenguage.get).get
                logger.info(s"??????????????${tweetInfo.toString}")
                tweetInfoDao.create(tweetInfo)
              }.recover({
                case e: Exception =>
                  logger.error(s"ERROR! ${e.printStackTrace()} ,${e.getMessage}")
              })
            }
            else {
              logger.info(s"??????????????${tweet.getText} NOT IDIOM CORRECT")
            })
        })
      }
      when { tweets =>
        logger.info(s"${~>}Received tweets [${tweets.count()}}]")
      }
      // ... and begin listening
      listen()

    }
}
}
object  PickUpWorker {
  val ~> = "[PICK-UP WORKER] "

  def apply()(implicit ndataContext: GathererDataContext): PickUpWorker =
    new PickUpWorker

}
