package results

import com.ita.domain.Id

/**
  * Created by mikelwyred on 19/08/2017.
  */
case class TweetResult(
  _id:Id,
  idTweet:Id,
  idExecution:Id,
  typeModel:String,
  result:Double)

