package com.ita.domain.gatherer

import com.ita.domain.Id

case class TweetPickUp(
  _id: Option[Id],
  topics: List[String],
  cuantity_warn: Long,
  nEmojis: Int,
  state: PickUpState,
  nTweets: Int,
  isRT: Boolean = false) {

  def canBeCreated:Boolean = ! List(Finished,InProcess,Ready).contains(state)

  def canBeCollected:Boolean =  List(Ready).contains(state)

  def canBeFinished:Boolean =  List(Finished).contains(state)


}


