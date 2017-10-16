package com.ita.classifier.utils

import com.ita.classifier.mongo._
import com.ita.common.mong.daos.{PickUpDAO, TweetInfoDAO}
import com.ita.domain.utils.Config


 case class ClassifierDataContext(
  pickupDAO: PickUpDAO,
  modelDAO: ModelDAO,
  modelResultDAO: ModelResultDAO,
  tweetResultDAO: TweetResultDAO,
  executionDAO: ModelExecutionlDAO,
  tweetsDAO: TweetInfoDAO,
   aggsDAO: ResultsAggslDAO,
   rT_RT_ResultsAggsDAO: RT_RT_ResultsAggsDAO)

object ClassifierDataContext extends Config{

  def chargeFromConfig():ClassifierDataContext ={
     val tweetInfoDao: TweetInfoDAO = TweetInfoDAO(
      config.getString("mongodb.host"),
      config.getInt("mongodb.port"),
      config.getString("mongodb.name"))

    val modelresultDAO: ModelResultDAO = ModelResultDAO(
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

    val aggsDAO:ResultsAggslDAO = ResultsAggslDAO(
      config.getString("mongodb.host"),
      config.getInt("mongodb.port"),
      config.getString("mongodb.name"))

    val rtAggsDAO = RT_RT_ResultsAggsDAO(config.getString("mongodb.host"),
      config.getInt("mongodb.port"),
      config.getString("mongodb.name"))



    new ClassifierDataContext(pickupDAO,modelDAO,modelresultDAO, resultDAO, executionlDAO,tweetInfoDao, aggsDAO,rtAggsDAO)


  }
}
