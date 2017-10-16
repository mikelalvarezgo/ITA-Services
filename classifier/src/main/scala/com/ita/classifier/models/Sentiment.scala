package com.ita.classifier.models

/**
  * Created by pswaminathan on 10/9/16.
  * Convert integer sentiment values returned by Stanford Core-NLP to an enumeration.
  */
object Sentiment extends Enumeration {
  type Sentiment = Value
  val TERRIBLE, SUPER_NICE,POSITIVE, NEGATIVE, NEUTRAL = Value

  def toSentiment(sentiment: Int): Sentiment = sentiment match {
    case 0 => Sentiment.TERRIBLE
    case 1 => Sentiment.NEGATIVE
    case 2 => Sentiment.NEUTRAL
    case 3 => Sentiment.POSITIVE
    case 4 => Sentiment.SUPER_NICE
  }

  def toInt(sentiment: Sentiment): Int = sentiment match {
    case  Sentiment.TERRIBLE=> 0
    case  Sentiment.NEGATIVE=> 1
    case  Sentiment.NEUTRAL=> 2
    case  Sentiment.POSITIVE=> 3
    case  Sentiment.SUPER_NICE=> 4
  }

  def to3class(sentiment: Sentiment): Int = sentiment match {
    case  Sentiment.TERRIBLE=> 0
    case  Sentiment.NEGATIVE=> 0
    case  Sentiment.NEUTRAL=> 1
    case  Sentiment.POSITIVE=> 2
    case  Sentiment.SUPER_NICE=> 2
  }
}
