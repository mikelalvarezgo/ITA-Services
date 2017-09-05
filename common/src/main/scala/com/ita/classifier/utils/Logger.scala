package com.ita.domain.utils

import org.slf4j.LoggerFactory


trait Logger {


  lazy val logger = LoggerFactory.getLogger(getClass.getName)
}
