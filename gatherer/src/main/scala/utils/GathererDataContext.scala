package utils

import domain.TweetInfo
import mongo.daos.{PickUpDAO, TweetInfoDAO}
import utils.DAOS.config

/**
  * Created by mikelalvarezgo on 17/6/17.
  */
 case class GathererDataContext(
  pickupDAO: PickUpDAO,
  tweetsDAO: TweetInfoDAO)

object GathererDataContext extends Config{

  def chargeFromConfig():GathererDataContext ={
     val tweetInfoDao: TweetInfoDAO = TweetInfoDAO(
      config.getString("mongodb.host"),
      config.getInt("mongodb.port"),
      config.getString("mongodb.database"))

     val pickupDAO: PickUpDAO = PickUpDAO(
      config.getString("mongodb.host"),
      config.getInt("mongodb.port"),
      config.getString("mongodb.database"))

    new GathererDataContext(pickupDAO,tweetInfoDao)
  }
}
