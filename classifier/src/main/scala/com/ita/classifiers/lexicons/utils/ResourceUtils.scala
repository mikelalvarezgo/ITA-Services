package com.ita.classifiers.lexicons.utils

import java.io.InputStream


 object ResourceUtils {

  def readFileAsList(path: String): Seq[String] = {
    val lines: Iterator[String] = scala.io.Source.fromFile(path).getLines

   lines.toList
  }
}
