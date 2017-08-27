package utils

import mongo.{ModelDAO, ModelExecutionlDAO, TweetResultDAO}
import mongo.daos.{PickUpDAO, TweetInfoDAO}

/**
  * Created by mikelalvarezgo on 17/6/17.
  */
 case class ClassifierDataContext(
  pickupDAO: PickUpDAO,
  modelDAO: ModelDAO,
  tweetResultDAO: TweetResultDAO,
  executionDAO: ModelExecutionlDAO,
  tweetsDAO: TweetInfoDAO)

object ClassifierDataContext extends Config{

  def chargeFromConfig():ClassifierDataContext ={
     val tweetInfoDao: TweetInfoDAO = TweetInfoDAO(
      config.getString("mongodb.host"),
      config.getInt("mongodb.port"),
      config.getString("mongodb.name"))

     val pickupDAO: PickUpDAO = PickUpDAO(
      config.getString("mongodb.host"),
      config.getInt("mongodb.port"),
      config.getString("mongodb.name"))

    val modelDAO:ModelDAO = ModelDAO(
      config.getString("mongodb.host"),
      config.getInt("mongodb.port"),
      config.getString("mongodb.name"))

    val resultDAO:TweetResultDAO = TweetResultDAO(
      config.getString("mongodb.host"),
      config.getInt("mongodb.port"),
      config.getString("mongodb.name"))

    val executionlDAO:ModelExecutionlDAO = ModelExecutionlDAO(
      config.getString("mongodb.host"),
      config.getInt("mongodb.port"),
      config.getString("mongodb.name"))


    new ClassifierDataContext(pickupDAO,modelDAO, resultDAO, executionlDAO,tweetInfoDao)


  }
}
