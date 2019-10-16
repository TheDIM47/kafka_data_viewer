package devtools.kafka_data_viewer

object ResourceUtility {
  def using[T, R](acquire: => T)(release: T => Unit)(use: T => R): R = {
    try {
      use(acquire)
    } finally {
      release()
    }
  }

  def withRes[T <: AutoCloseable, R](acquire: => T)(use: T => R): R =
    using(acquire)(t => t.close())(use)
}
