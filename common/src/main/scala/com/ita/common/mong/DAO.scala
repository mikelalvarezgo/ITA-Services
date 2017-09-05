package com.ita.common.mongo.daos.mongo

import com.ita.domain.Id

import scala.util.Try

/**
  * A [[DAO]] actually represents the data access layer for any T value.
  * It will be mainly used for accessing some primary database.
  */
trait DAO[T] {

  def getAll: Stream[T]

  def create(t: T): Try[Unit]

  def update(t: T): Try[Unit]

  def get(id: Id): Try[T]

  def remove(id: Id): Try[Unit]

  /**
    * Paginated 'get'
    */
  def get(page: Int, pageSize: Int, sortField: Option[String], sortAsc: Option[Boolean]): Try[List[T]]

  /**
    * Get latest element of T type that was created.
    */
  def latest[U](sorting: T => U)(implicit o: Ordering[U]): Try[T]

}
