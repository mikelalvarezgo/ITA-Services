package com.ita.gatherer.actors

import com.ita.domain.gatherer.TweetPickUp

object ActorMessages {
  case class StartPickUp(tweetPickUp: TweetPickUp)
  case class CollectPickUp(tweetPickUp: TweetPickUp)
  case class StopPickUp(tweetPickUp: TweetPickUp)
  case object AllWorkersBusy
  case object WorkerExists
  case object WorkerNotExists
  case object WorkerStarted
  case object WorkerStopped



}
