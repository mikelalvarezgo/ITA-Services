package domain.gatherer

import domain.Id

case class TweetPickUp(
  _id: Option[Id],
  topics: List[String],
  cuantity_warn: Long,
  nEmojis: Int,
  state: PickUpState,
  nTweets: Int) {

  def canBeInit:Boolean = ! List(Finished,InProcess).contains(state)

  def canBeFinished:Boolean =  List(Finished).contains(state)


}


