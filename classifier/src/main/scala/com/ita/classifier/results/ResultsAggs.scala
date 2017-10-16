package com.ita.classifier.results

import com.ita.classifier.models.ModelData
import com.ita.domain.Id
import opennlp.tools.ml.model.AbstractModel.ModelType


case class ResultsAggs(
  _id: Id,
  topic_id: Id,
  model: ModelData,
  positive: Long,
  negative: Long,
  neutral: Long)
