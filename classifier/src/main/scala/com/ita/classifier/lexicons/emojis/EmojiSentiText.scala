package com.ita.classifier.lexicons.emojis


import com.ita.classifiers.lexicons.utils.ResourceUtils
import com.ita.domain.utils.Config

import scala.collection.{Map, Seq}
import com.ita.classifiers.lexicons.utils.ResourceUtils
import com.ita.domain.utils.Config

import scala.collection.mutable.ListBuffer
import scala.collection.{Seq, _}
import scala.util.control.Breaks._

/**
  * Created by mikelwyred on 05/07/2017.
  */
object EmojiSentiText extends Config {


  val ExclIncr: Double = 0.292
  val QuesIncrSmall: Double = 0.18
  val QuesIncrLarge: Double = 0.96
  val NEGATIVE_EMOJI_LEXICON_PATH = config.getString("lexicon.emoji_path.negative")
  val NEGATIVE_WORD_LEXICON_PATH = config.getString("lexicon.word_path.negative")

  val POSITIVE_EMOJI_LEXICON_PATH = config.getString("lexicon.emoji_path.positive")
  val POSITIVE_WORD_LEXICON_PATH = config.getString("lexicon.word_path.positive")

  def makeLexDict(lexiconFile: Seq[String],score:Int): Map[String, Int] = {
    var result = Map[String, Int]()

    for (line <- lexiconFile) {
      val lineArray = line.trim().split('\t')
      result += (lineArray(0) -> score)
    }

    result
  }

  val negativeemojiLexicon =
    makeLexDict(ResourceUtils.readFileAsList(NEGATIVE_EMOJI_LEXICON_PATH),0)
  val negativewordLexicon =
    makeLexDict(ResourceUtils.readFileAsList(NEGATIVE_WORD_LEXICON_PATH),0)
  val positiveemojiLexicon =
    makeLexDict(ResourceUtils.readFileAsList(POSITIVE_EMOJI_LEXICON_PATH),0)
  val positivewordLexicon =
    makeLexDict(ResourceUtils.readFileAsList(POSITIVE_WORD_LEXICON_PATH),0)


  val wordLexicon =positivewordLexicon ++ negativewordLexicon
  val emojiLexicon =positiveemojiLexicon ++ negativeemojiLexicon

    private case class SiftSentiments(var posSum: Double = 0, var negSum: Double = 0, var neuCount: Int = 0)

    def totalPolarityScores(input:String): SentimentAnalysisResults = {
      val scoresEmoji = emojipolarityScores(input)
      val scoresText = wordpolarityScores(input)
      SentimentAnalysisResults(
        (scoresEmoji.negative * 2 + scoresText.negative)/3,
        (scoresEmoji.neutral * 2 + scoresText.neutral)/3,
        (scoresEmoji.positive * 2 + scoresText.positive)/3,
        (scoresEmoji.compound * 2 + scoresText.compound)/3)
    }
    def wordpolarityScores(input: String): SentimentAnalysisResults = {
      val sentiText: SentiText = new SentiText(input)
      var sentiments: ListBuffer[Double] = ListBuffer[Double]()

      val wordsAndEmoticons = sentiText.wordsAndEmoticons

      breakable {
        for ((item, i) <- wordsAndEmoticons.view.zipWithIndex) {

          var valence: Double = 0

          if (i < wordsAndEmoticons.size - 1
            && item.toLowerCase() == "kind" && wordsAndEmoticons(i + 1) == "of"
            || SentimentUtils.boosterDict.contains(item.toLowerCase())) {
            sentiments += valence
            break
          }

          val (_valence, _sentiments) = sentimentValence(wordLexicon,valence, sentiText, item, i, sentiments)
          valence = _valence
          sentiments = _sentiments
        }
      }

      sentiments = butCheck(wordsAndEmoticons, sentiments)

      scoreValence(sentiments, input)
    }
    def emojipolarityScores(input: String): SentimentAnalysisResults = {
      val sentiText: SentiText = new SentiText(input)
      var sentiments: ListBuffer[Double] = ListBuffer[Double]()

      val wordsAndEmoticons = sentiText.wordsAndEmoticons

      breakable {
        for ((item, i) <- wordsAndEmoticons.view.zipWithIndex) {

          var valence: Double = 0

          if (i < wordsAndEmoticons.size - 1
            && item.toLowerCase() == "kind" && wordsAndEmoticons(i + 1) == "of"
            || SentimentUtils.boosterDict.contains(item.toLowerCase())) {
            sentiments += valence
            break
          }

          val (_valence, _sentiments) = sentimentValence(emojiLexicon,valence, sentiText, item, i, sentiments)
          valence = _valence
          sentiments = _sentiments
        }
      }

      sentiments = butCheck(wordsAndEmoticons, sentiments)

      scoreValence(sentiments, input)
    }


    def sentimentValence(lexicon:Map[String,Int],valenc: Double, sentiText: SentiText,
      item: String, i: Int, sentiments: ListBuffer[Double]): (Double, ListBuffer[Double]) = {

      var valence = valenc
      val itemLowerCase: String = item.toLowerCase()
      if (!lexicon.contains(itemLowerCase)) {
        sentiments += valence
        return (valence, sentiments)
      }

      val isCapDiff: Boolean = sentiText.isCapDifferential
      val wordsAndEmoticons = sentiText.wordsAndEmoticons
      valence = lexicon(itemLowerCase)

      if (isCapDiff && SentimentUtils.isUpper(item)) {
        if (valence > 0) {
          valence += SentimentUtils.CIncr
        } else {
          valence -= SentimentUtils.CIncr
        }
      }

      for (startI <- 0 until 3) {
        if (i > startI && !lexicon.contains(wordsAndEmoticons(i - (startI + 1)).toLowerCase())) {
          var s: Double = SentimentUtils.scalarIncDec(wordsAndEmoticons(i - (startI + 1)), valence, isCapDiff)

          if (startI == 1 && s != 0) {
            s = s * 0.95
          }

          if (startI == 2 && s != 0) {
            s = s * 0.9
          }

          valence = valence + s

          valence = neverCheck(valence, wordsAndEmoticons, startI, i)

          if (startI == 2) {
            valence = idiomsCheck(valence, wordsAndEmoticons, i)
          }

        }
      }

      valence = leastCheck(lexicon,valence, wordsAndEmoticons, i)
      sentiments += valence

      (valence, sentiments)
    }


    def scoreValence(sentiments: Seq[Double], text: String): SentimentAnalysisResults = {

      if (sentiments.isEmpty) {
        return SentimentAnalysisResults() //will return with all 0
      }

      var sum: Double = sentiments.sum
      var puncAmplifier: Double = punctuationEmphasis(text)

      sum += scala.math.signum(sum) * puncAmplifier //Sign

      val compound: Double = SentimentUtils.normalize(sum)
      val sifted: SiftSentiments = siftSentimentScores(sentiments)

      if (sifted.posSum > scala.math.abs(sifted.negSum)) {
        sifted.posSum += puncAmplifier
      }
      else if (sifted.posSum < scala.math.abs(sifted.negSum)) {
        sifted.negSum -= puncAmplifier
      }

      val total: Double = sifted.posSum + scala.math.abs(sifted.negSum) + sifted.neuCount

      SentimentAnalysisResults(
        compound = roundWithDecimalPlaces(compound, 4),
        positive = roundWithDecimalPlaces(scala.math.abs(sifted.posSum / total), 3),
        negative = roundWithDecimalPlaces(scala.math.abs(sifted.negSum / total), 3),
        neutral = roundWithDecimalPlaces(scala.math.abs(sifted.neuCount / total), 3)
      )

    }


    def idiomsCheck(valenc: Double, wordsAndEmoticons: Seq[String], i: Int): Double = {

      var valence = valenc


      val oneZero = wordsAndEmoticons(i - 1).concat(" ").concat(wordsAndEmoticons(i))
      val twoOneZero = wordsAndEmoticons(i - 2)
        .concat(" ").concat(wordsAndEmoticons(i - 1)).concat(" ").concat(wordsAndEmoticons(i))

      val twoOne = wordsAndEmoticons(i - 2)
        .concat(" ").concat(wordsAndEmoticons(i - 1))
      val threeTwoOne = wordsAndEmoticons(i - 3)
        .concat(" ").concat(wordsAndEmoticons(i - 2)).concat(" ").concat(wordsAndEmoticons(i - 1))
      val threeTwo = wordsAndEmoticons(i - 3).concat(" ").concat(wordsAndEmoticons(i - 2))

      val sequences = Array(oneZero, twoOneZero, twoOne, threeTwoOne, threeTwo)

      breakable {
        for (seq <- sequences) {
          if (SentimentUtils.specialCaseIdioms.contains(seq)) {
            valence = SentimentUtils.specialCaseIdioms(seq)
            break
          }
        }
      }

      if (wordsAndEmoticons.size - 1 > i) {
        val zeroOne = wordsAndEmoticons(i).concat(" ").concat(wordsAndEmoticons(i + 1))
        if (SentimentUtils.specialCaseIdioms.contains(zeroOne)) {
          valence = SentimentUtils.specialCaseIdioms(zeroOne)
        }
      }
      if (wordsAndEmoticons.size - 1 > i + 1) {
        val zeroOneTwo = wordsAndEmoticons(i)
          .concat(" ").concat(wordsAndEmoticons(i + 1)).concat(" ").concat(wordsAndEmoticons(i + 2))
        if (SentimentUtils.specialCaseIdioms.contains(zeroOneTwo)) {
          valence = SentimentUtils.specialCaseIdioms(zeroOneTwo)
        }
      }
      if (SentimentUtils.boosterDict.contains(threeTwo) || SentimentUtils.boosterDict.contains(twoOne)) {
        valence += SentimentUtils.BDecr
      }

      valence
    }

    def leastCheck(lexicon:Map[String,Int],valenc: Double, wordsAndEmoticons: Seq[String], i: Int): Double = {

      var valence: Double = valenc

      if (i > 1 && !lexicon.contains(wordsAndEmoticons(i - 1).toLowerCase()) &&
        wordsAndEmoticons(i - 1).toLowerCase() == "least") {
        if (wordsAndEmoticons(i - 2).toLowerCase() != "at" && wordsAndEmoticons(i - 2).toLowerCase() != "very") {
          valence = valence * SentimentUtils.NScalar
        }
      }
      else if (i > 0 && !lexicon.contains(wordsAndEmoticons(i - 1).toLowerCase())
        && wordsAndEmoticons(i - 1).toLowerCase() == "least") {
        valence = valence * SentimentUtils.NScalar
      }

      valence
    }

    def neverCheck(valenc: Double, wordsAndEmoticons: Seq[String], startI: Int, i: Int): Double = {

      var valence = valenc
      startI match {
        case 0 => {
          val list = List(wordsAndEmoticons(i - 1))
          if (SentimentUtils.negated(list)) {
            valence = valence * SentimentUtils.NScalar
          }
        }
        case 1 => {
          if (wordsAndEmoticons(i - 2) == "never" &&
            (wordsAndEmoticons(i - 1) == "so" || wordsAndEmoticons(i - 1) == "this")) {
            valence = valence * 1.5
          }
          else if (SentimentUtils.negated(List(wordsAndEmoticons(i - (startI + 1))))) {
            valence = valence * SentimentUtils.NScalar
          }
        }
        case 2 => {
          if (wordsAndEmoticons(i - 3) == "never"
            && (wordsAndEmoticons(i - 2) == "so" || wordsAndEmoticons(i - 2) == "this")
            || (wordsAndEmoticons(i - 1) == "so" || wordsAndEmoticons(i - 1) == "this")) {
            valence = valence * 1.25
          }
          else if (SentimentUtils.negated(List(wordsAndEmoticons(i - (startI + 1))))) {
            valence = valence * SentimentUtils.NScalar
          }
        }
      }

      valence
    }

    def butCheck(wordsAndEmoticons: Seq[String], sentiments: ListBuffer[Double]): ListBuffer[Double] = {

      val containsBUT: Boolean = wordsAndEmoticons.contains("BUT")
      val containsbut: Boolean = wordsAndEmoticons.contains("but")

      if (!containsBUT && !containsbut) {
        return sentiments
      }

      val butIndex: Int = if (containsBUT) wordsAndEmoticons.indexOf("BUT") else wordsAndEmoticons.indexOf("but")

      for ((sentiment, i) <- sentiments.view.zipWithIndex) {

        if (i < butIndex) {
          sentiments.remove(i)
          sentiments.insert(i, sentiment * 0.5)
        }
        else if (i > butIndex) {
          sentiments.remove(i)
          sentiments.insert(i, sentiment * 1.5)
        }
      }

      sentiments
    }


    private def punctuationEmphasis(text: String): Double = {
      amplifyExclamation(text) + amplifyQuestion(text)
    }

    private def amplifyExclamation(text: String): Double = {
      var epCount: Int = text.count(x => x == '!')

      if (epCount > 4) {
        epCount = 4
      }

      epCount * ExclIncr
    }

    private def amplifyQuestion(text: String): Double = {
      val qmCount: Int = text.count(x => x == '?')

      if (qmCount < 1) {
        return 0
      }

      if (qmCount <= 3) {
        return qmCount * QuesIncrSmall
      }

      QuesIncrLarge
    }

    private def siftSentimentScores(sentiments: Seq[Double]): SiftSentiments = {
      val siftSentiments = SiftSentiments()

      for (sentiment <- sentiments) {
        if (sentiment > 0) {
          siftSentiments.posSum += (sentiment + 1); //1 compensates for neutrals
        }

        if (sentiment < 0) {
          siftSentiments.negSum += (sentiment - 1)
        }

        if (sentiment == 0) {
          siftSentiments.neuCount += 1
        }
      }

      siftSentiments
    }


    private def roundWithDecimalPlaces(value: Double, decPlaces: Int): Double = {
      decPlaces match {
        case 1 => (value * 10).round / 10.toDouble
        case 2 => (value * 100).round / 100.toDouble
        case 3 => (value * 1000).round / 1000.toDouble
        case 4 => (value * 10000).round / 10000.toDouble
      }
    }
  }

