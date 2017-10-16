package com.ita.classifier.results

import com.ita.domain.Id

/**
  * Created by mikelwyred on 19/06/2017.
  */
case class ModelResult(
  _id: Id,
  model_id: Id,
  train_accuracy: Double,
  valid_accuracy: Double,
  test_accuracy:Double)


