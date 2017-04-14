package domain.gatherer

import domain.Id

case class TweetPickUp(
  _id: Id,
  topics: List[String],
  cuantity_warn: Long,
  state: PickUpState,
  nTweets: Int) {

}


