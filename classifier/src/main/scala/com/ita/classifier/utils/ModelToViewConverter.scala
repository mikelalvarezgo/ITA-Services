package com.ita.classifier.utils

trait ModelToViewConverter[M, V] {
  def toView(m: M): V
  def toModel(v: V): M
}

object ModelToViewConverter {
  trait ModelToViewConverterDSL {
    implicit class ViewModelHelper[T](t: T) {
      def toView[V](implicit ev: ModelToViewConverter[T, V]): V = ev.toView(t)
      def toModel[M](implicit ev: ModelToViewConverter[M, T]): M = ev.toModel(t)
    }
  }
}
