package mdome7.prometheusaws.config

import java.io.File
import java.nio.file.Paths

import com.typesafe.config.{Config, ConfigFactory}
import mdome7.prometheusaws.aws.TargetHostAttribute
import mdome7.prometheusaws.output.{OutputFormat, YAMLFormat}

import scala.collection.JavaConversions._

/**
  * Config file must be on HOCON format (https://github.com/lightbend/config/blob/master/HOCON.md#array-and-object-concatenation)
  * and contain a list of entries
  */
object ConfigurationLoader {

  val KEY_AWS_ACCESS_KEY = "aws.access-key"
  val KEY_AWS_SECRET_KEY = "aws.secret-key"
  val KEY_AWS_REGION     = "aws.region"
  val KEY_REFRESH_PERIOD = "refresh-period"
  val KEY_TARGET_CONFIGS = "targets"
  val KEY_SEARCH_CONFIG  = "search"
  val KEY_MODE           = "mode"
  val KEY_HOST_ATTRIBUTE = "host-attribute"
  val KEY_OUTPUT_DIR     = "output.dir"
  val KEY_OUTPUT_FORMAT  = "output.format"

  val DEFAULT_OUTPUT_FORMAT = YAMLFormat

  val defaultConfig = ConfigFactory.load().getConfig("defaults") // loads reference.conf

  def loadFile(file: File): Configuration = {
    val config = ConfigFactory.parseFile(file).resolve()
    parseConfiguration(config)
  }

  /**
    * Parse the HOCON config file to generate the Configuration
    * @param config
    * @return
    */
  def parseConfiguration(config: Config): Configuration = {
    val awsConfig = AWSConfig(config.getString(KEY_AWS_ACCESS_KEY), config.getString(KEY_AWS_SECRET_KEY), config.getString(KEY_AWS_REGION))
    val refreshPeriod = if (config.hasPath(KEY_REFRESH_PERIOD)) config.getDuration(KEY_REFRESH_PERIOD) else defaultConfig.getDuration(KEY_REFRESH_PERIOD)
    val targets = config.getConfigList(KEY_TARGET_CONFIGS).map(_.resolve).map(parseTargetConfig)
    val outputDir = Paths.get(getStringOrDefault(config, KEY_OUTPUT_DIR, defaultConfig.getString(KEY_OUTPUT_DIR)))
    val outputFormat = OutputFormat.fromCode(getStringOrDefault(config, KEY_OUTPUT_FORMAT, DEFAULT_OUTPUT_FORMAT.code)).getOrElse(DEFAULT_OUTPUT_FORMAT)
    Configuration(awsConfig, refreshPeriod, targets, outputDir, outputFormat)
  }

  private def parseTargetConfig(target: Config): TargetConfig = {
    val name = target.getString("name")
    val searchMode = target.getString(s"${KEY_SEARCH_CONFIG}.${KEY_MODE}")
    val search: SearchConfig = searchMode match {
      case SearchMode.EC2 => parseEC2SearchConfig(target.getConfig(KEY_SEARCH_CONFIG).resolve)
      case SearchMode.ECS => parseECSSearchConfig(target.getConfig(KEY_SEARCH_CONFIG).resolve)
      case _ => throw new InvalidConfigException(
        s"""${KEY_SEARCH_CONFIG}.${KEY_MODE} value "${searchMode}" is not supported.
           | Valid values are: ${SearchMode.values}""".stripMargin)
    }
    val metrics = parseMetricsConfig(target.getConfig("metrics").resolve)

    TargetConfig(name, search, metrics)
  }

  private def parseEC2SearchConfig(config: Config): EC2SearchConfig = {
    val filters = parseMapFromPath(config, "filters")
    val tags = parseMapFromPath(config, "tags")
    EC2SearchConfig(filters, tags)
  }

  private def parseECSSearchConfig(config: Config) = ECSSearchConfig(config.getString("cluster"), config.getString("service"))

  private def parseMetricsConfig(config: Config): MetricsConfig = {
    val hostAttribute = TargetHostAttribute.fromString(getExpectedString(config, KEY_HOST_ATTRIBUTE))
      .getOrElse(throw new InvalidConfigException(s"""Host attribute "${config.getString(KEY_HOST_ATTRIBUTE)}" is not supported"""))
    val labels = parseMapFromPath(config, "labels")
    MetricsConfig(config.getIntList("ports").map(i => i.toInt), hostAttribute, labels)
  }

  private def parseMapFromPath(config: Config, path: String): Map[String,String] =
    if (config.hasPath(path)) parseMap(config.getConfig(path).resolve) else Map.empty[String,String]

  private def parseMap(config: Config): Map[String,String] =
    config.entrySet().map(entry => entry.getKey -> entry.getValue.unwrapped.toString).toMap

  private def getExpectedString(config: Config, key: String) =
    if (config.hasPath(key)) config.getString(key)
    else throw new InvalidConfigException(s"""Missing entry ${key}""")

  private def getStringOrDefault(config: Config, key: String, defaultValue: String) =
    if (config.hasPath(key)) config.getString(key)
    else defaultValue
}
