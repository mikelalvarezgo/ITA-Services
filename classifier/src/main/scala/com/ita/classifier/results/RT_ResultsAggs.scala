package com.ita.classifier.results

import com.ita.classifier.models.ModelData
import com.ita.domain.Id

/**
  * Created by mikelwyred on 13/09/2017.
  */
case class RT_ResultsAggs(
  _id: Id,
  topic_id: Id,
  positive: Long,
  negative: Long,
  neutral: Long)