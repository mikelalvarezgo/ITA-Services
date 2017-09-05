package utils

trait Filters {

    private var _filters: Seq[String] = Seq()

    def filters = synchronized(_filters)

    def filter(filters: List[String]): Unit = {
      synchronized(_filters = _filters ++ filters)
    }

  }

