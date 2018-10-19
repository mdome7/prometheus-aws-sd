package mdome7.prometheusaws.output

sealed trait OutputFormat { def code: String }

object OutputFormat {
  def fromCode(code: String): Option[OutputFormat] = {
    val codeLo = code.toLowerCase
    values.find(_.code == codeLo)
  }

  def values = Seq(YAMLFormat, JSONFormat)
}

case object YAMLFormat extends OutputFormat{ val code = "yaml" }
case object JSONFormat extends OutputFormat{ val code = "json" }