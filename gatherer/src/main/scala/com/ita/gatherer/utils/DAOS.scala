package com.ita.gatherer.utils

import com.ita.common.mong.daos.TweetInfoDAO
import com.ita.domain.utils.{Config, Logger}

trait  DAOS {_: Config =>


}

object DAOS extends Logger with Config{

  logger.info(s"MONGO HOST : ${config.getString("mongodb.host")}, " +
    s"MONGO PORT : ${config.getString("mongodb.host")} " +
    s"MONGO DATABASE : ${config.getString("mongodb.database")}")
  lazy val tweetInfoDao: TweetInfoDAO = TweetInfoDAO(
    config.getString("mongodb.host"),
    config.getInt("mongodb.port"),
    config.getString("mongodb.database"))
}
