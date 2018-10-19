package mdome7.prometheusaws.output

import java.io.{File, FileWriter, PrintWriter}
import java.nio.file.{Files, Path}
import java.time.LocalDateTime

import com.typesafe.scalalogging.LazyLogging
import mdome7.prometheusaws.config.{InvalidConfigException, TargetConfig}
import mdome7.prometheusaws.util.JSONUtil
import mdome7.prometheusaws.output.OutputWriter.{INDENT, INDENT_X2}

import scala.util.Try

class OutputWriter(outputDir: Path) extends LazyLogging {

  def write(targetConfig: TargetConfig, targetGroup: TargetGroup, format: OutputFormat = YAMLFormat): Try[Path] = {
    checkOrCreateDir()

    format match {
      case JSONFormat =>
        val outputPath = outputDir.resolve("%s.json".format(targetConfig.name.replaceAll(" ", "_")))
        writeToPath(outputPath) { outputFile =>
          JSONUtil.toJsonFile(targetGroup, outputFile, true)
        }
      case YAMLFormat =>
        val outputPath = outputDir.resolve("%s.yml".format(targetConfig.name.replaceAll(" ", "_")))
        writeToPath(outputPath) { outputFile =>
          writeYAML(outputFile, targetConfig, targetGroup)
        }
    }

  }

  def writeYAML(outputFile: File, targetConfig: TargetConfig, targetGroup: TargetGroup): Unit = {
    val pw = new PrintWriter(new FileWriter(outputFile))
    logger.info(s"Writing to ${outputFile.getAbsolutePath}")
    val comments = OutputWriter.generateComments(targetConfig)
    comments.map(c => pw.println(s"# ${c}"))
    pw.println("- targets:")
    targetGroup.targets.map(t => pw.println(s"${INDENT_X2}- ${t}"))

    if (targetGroup.labels.nonEmpty) {
      pw.println(s"${INDENT}labels:")
      targetGroup.labels.map({ case(key, value) => pw.println(s"""${INDENT_X2}${key}: "${value}"""") })
    }
    pw.flush()
    pw.close()
  }


  def writeToPath(path: Path)(code: File => Unit): Try[Path] = Try{
    deleteIfExists(path)
    logger.debug(s"Begin writing to file: ${path}")
    val file = path.toFile
    code(file)
    logger.debug(s"Done writing output: ${path}")
    path
  }


  def deleteIfExists(path: Path): Boolean = {
    if (Files.exists(path)) {
      logger.warn(s"Deleting existing file: ${path}")
    }
    Files.deleteIfExists(path)
  }

  def checkOrCreateDir(): Unit = {
    if (!Files.exists(outputDir)) {
      logger.info(s"Creating output directory: ${outputDir}")
      Files.createDirectories(outputDir)
    } else if (!Files.isDirectory(outputDir)) {
      throw new InvalidConfigException(s"Ouput path is not a directory: ${outputDir}")
    }
  }
}

object OutputWriter {
  val INDENT = "  "
  val INDENT_X2 = INDENT * 2

  def generateComments(target: TargetConfig): Seq[String] =
    Seq(
      s"--- ${target.name} ---",
      s"Generated on: ${LocalDateTime.now()}",
      s"Search (${target.search.mode}): ${target.search}",
      s"Host attribute: ${target.metrics.hostAttribute}"
    )
}