package com.ita.classifier.results

import com.ita.domain.Id

/**
  * Created by mikelwyred on 19/06/2017.
  */
case class ModelResult(
  _id: Id,
  model_id: Id,
  train_positive_good_classified: Int,
  trainpositive_bad_classified: Int,
  train_porc_positived:Double,
  trainnegative_good_classified: Int,
  trainnegative_bad_classified: Int,
  train_porc_negative:Double,
  trainneutral_good_classified: Int,
  trainneutral_bad_classified: Int,
  train_porc_neutral:Double,
  val_positive_good_classified: Int,
  val_positive_bad_classified: Int,
  porc_positived:Double,
  val_negative_good_classified: Int,
  val_negative_bad_classified: Int,
  porc_negative:Double,
  val_neutral_good_classified: Int,
  val_neutral_bad_classified: Int,
  porc_neutral:Double){

}
