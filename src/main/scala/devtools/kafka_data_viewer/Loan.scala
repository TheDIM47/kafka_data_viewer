package devtools.kafka_data_viewer

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.Exception.ignoring

object Loan extends LoanT

/**
  * https://github.com/scalikejdbc/scalikejdbc/blob/master/scalikejdbc-core/src/main/scala/scalikejdbc/LoanPattern.scala
  * Loan pattern implementation
  */
trait LoanT {
  type Closable = { def close(): Unit }

  def using[R <: Closable, A](resource: R)(f: R => A): A =
    try f(resource)
    finally ignoring(classOf[Throwable]) apply {
      resource.close()
    }

  /**
    * Guarantees a Closeable resource will be closed after being passed to a block that takes
    * the resource as a parameter and returns a Future.
    */
  def futureUsing[R <: Closable, A](resource: R)(
    f: R => Future[A]
  )(implicit ec: ExecutionContext): Future[A] = f(resource) andThen {
    case _ => resource.close()
  } // close no matter what
}
