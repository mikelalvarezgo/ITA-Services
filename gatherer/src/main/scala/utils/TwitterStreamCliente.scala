package utils


import akka.actor.ActorSystem
import twitter4j._
import twitter4j.auth.AccessToken
import twitter4j.conf.ConfigurationBuilder
import TwitterCredentials._
import domain.TweetInfo
import utils.DAOS.tweetInfoDao

import scala.collection._

final case class Author(handle: String)

final case class Hashtag(name: String)

case class Tweet(author: Author, timestamp: Long, body: String) {
  def hashtags: Set[Hashtag] =
    body.split(" ").collect { case t if t.startsWith("#") => Hashtag(t) }.toSet
}

final object EmptyTweet extends Tweet(Author(""), 0L, "")


class TwitterStreamClient(val actorSystem: ActorSystem, filters: List[String]) extends Config
with Logger
with TwitterCredentials{
  val twitterStream = new TwitterStreamFactory().getInstance()
  val appKey =  config.getString(ConsumerKey)
  val appSecret =  config.getString(ConsumerSecret)
  val token = config.getString("twitter4j.oauth.accessToken")
  val tokenSecret = config.getString(AccessTokenSecret)



  def init = {
    twitterStream.setOAuthConsumer(appKey, appSecret)
    twitterStream.setOAuthAccessToken(new AccessToken(token,tokenSecret))
    twitterStream.addListener(simpleUserListener)
    twitterStream.user()
    val q : FilterQuery = new FilterQuery(0,null,filters.toArray);
    twitterStream.filter(q);
    twitterStream
  }
  val idiomsFilter = config.getStringList("twitter.lenguages").asInstanceOf[List[String]]
    .map{ leng => TweetsFilter(s"filter_$leng",Some(leng))}

  def simpleUserListener = new UserStreamListener {
    def onStatus(tweet: Status) {
        idiomsFilter foreach(filter =>
          if(filter.isTweetLenguage(tweet.getText)) {
              val tweetInfo = TweetInfo.content2TwitterInfo(tweet, filter.lenguage.get).get
              logger.info(s"??????????????${tweetInfo.toString}")
              actorSystem.eventStream.publish(TweetInfo)
          }else {
              logger.info(s"?????????????? NOT CORRECT LENGUAGE")
          })
    }
    override def onFriendList(friendIds: Array[Long]) = {}

    override def onUserListUnsubscription(subscriber: User, listOwner: User, list: UserList) = {}

    override def onBlock(source: User, blockedUser: User) = {}

    override def onUserListSubscription(subscriber: User, listOwner: User, list: UserList) = {}

    override def onFollow(source: User, followedUser: User) = {}

    override def onUserListMemberAddition(addedMember: User, listOwner: User, list: UserList) = {}

    override def onDirectMessage(directMessage: DirectMessage) = {}

    override def onUserListUpdate(listOwner: User, list: UserList) = {}

    override def onUnblock(source: User, unblockedUser: User) = {}

    def onUnfollow(source: User, unfollowedUser: User) = {}

    override def onUserProfileUpdate(updatedUser: User) = {}

    override def onUserListMemberDeletion(deletedMember: User, listOwner: User, list: UserList) = {}

    override def onDeletionNotice(directMessageId: Long, userId: Long) = {}

    override def onFavorite(source: User, target: User, favoritedStatus: Status) = {}

    override def onUnfavorite(source: User, target: User, unfavoritedStatus: Status) = {}

    override def onUserListDeletion(listOwner: User, list: UserList) = {}

    override def onUserListCreation(listOwner: User, list: UserList) = {}

    override def onStallWarning(warning: StallWarning) = {}

    override def onDeletionNotice(statusDeletionNotice: StatusDeletionNotice) = {}

    override def onScrubGeo(userId: Long, upToStatusId: Long) = {}

    override def onTrackLimitationNotice(numberOfLimitedStatuses: Int) = {}

    override def onException(ex: Exception) = {}
  }

  def stop = {
    twitterStream.cleanUp
    twitterStream.shutdown
  }

}