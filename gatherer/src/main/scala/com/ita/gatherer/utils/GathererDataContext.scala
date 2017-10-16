package com.ita.gatherer.utils

import com.ita.common.mong.daos.{PickUpDAO, TweetInfoDAO}
import com.ita.domain.utils.Config

 case class GathererDataContext(
  pickupDAO: PickUpDAO,
  tweetsDAO: TweetInfoDAO)

object GathererDataContext extends Config{

  def chargeFromConfig():GathererDataContext ={
     val tweetInfoDao: TweetInfoDAO = TweetInfoDAO(
      config.getString("mongodb.host"),
      config.getInt("mongodb.port"),
      config.getString("mongodb.name"))

     val pickupDAO: PickUpDAO = PickUpDAO(
      config.getString("mongodb.host"),
      config.getInt("mongodb.port"),
      config.getString("mongodb.name"))

    new GathererDataContext(pickupDAO,tweetInfoDao)
  }
}
