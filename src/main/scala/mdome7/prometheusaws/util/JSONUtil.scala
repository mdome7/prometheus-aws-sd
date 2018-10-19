package mdome7.prometheusaws.util

import java.io.File

import com.fasterxml.jackson.annotation.JsonInclude.Include
import com.fasterxml.jackson.databind.{DeserializationFeature, ObjectMapper}
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import com.fasterxml.jackson.module.scala.experimental.ScalaObjectMapper

object JSONUtil {
  val objectMapper = new ObjectMapper() with ScalaObjectMapper
  objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
  objectMapper.setSerializationInclusion(Include.NON_NULL)
  objectMapper.setSerializationInclusion(Include.NON_EMPTY)
  objectMapper.registerModule(DefaultScalaModule)

  def fromJson[T](json: String)(implicit m : Manifest[T]): T = {
    objectMapper.readValue[T](json)
  }

  def toJson(value: Any, prettyFormat: Boolean = true): String =
    if (prettyFormat) {
      objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(value)
    } else {
      objectMapper.writeValueAsString(value)
    }

  def toJsonFile(value: Any, outputFile: File, prettyFormat: Boolean = true): Unit = {
    objectMapper.writerWithDefaultPrettyPrinter().writeValue(outputFile, value)
  }
}
