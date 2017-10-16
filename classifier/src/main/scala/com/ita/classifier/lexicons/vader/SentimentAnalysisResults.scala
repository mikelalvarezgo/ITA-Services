package com.ita.classifier.lexicons.vader

case class SentimentAnalysisResults(negative: Double=0,
                                    neutral: Double=0,
                                    positive: Double=0,
                                    compound: Double=0) {

  /*
  positive sentiment: compound score >= 0.5
neutral sentiment: (compound score > -0.5) and (compound score < 0.5)
negative sentiment: compound score <= -0.5 */

  def to3Class:Double = {
    if (compound >= 0.5)
      2.0
    else if (compound <= -0.2)
      0.0
    else
      1.0
  }
  def to2Class:Double = {
    if (compound >= 0.0)
      1.0
    else
      0.0
  }
}