package mdome7.prometheusaws.util

import com.typesafe.scalalogging.LazyLogging

import scala.util.Try

object CloseableUtil extends LazyLogging {

  /**
    * Emulate Java's try-with-resources statement.
    *
    * tryWithResource(new BufferedReader(new FileReader(path))) { reader => reader.readLine() }
    * @param resource
    * @param code
    * @tparam R
    * @tparam A
    * @return
    */
  def tryWithResource[R<:AutoCloseable,A](resource: => R)(code: R => A): Try[A] = {
    Try{
      try {
        code(resource)
      } finally {
        logger.info(s"Closing ${resource}")
        closeQuietly(resource)
      }
    }
  }

  def closeQuietly(closeable: AutoCloseable): Unit = {
    try {
      if (closeable != null) closeable.close()
    } catch {
      case t: Throwable => logger.trace(s"failed to close ${closeable.getClass} - ${t.getMessage}")
    }
  }

}
