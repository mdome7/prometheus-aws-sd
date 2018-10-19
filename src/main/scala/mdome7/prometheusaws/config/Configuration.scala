package mdome7.prometheusaws.config

import java.nio.file.Path
import java.time.Duration

import mdome7.prometheusaws.aws.TargetHostAttribute
import mdome7.prometheusaws.output.{OutputFormat, YAMLFormat}

/**
  * Main configuration that tells the application how to search for hosts in AWS
  * and how to configure the corresponding targets in the output for Prometheus.
  * @param aws
  * @param refreshPeriod
  * @param targets
  * @param outputDir
  * @param outputFormat
  */
case class Configuration(aws: AWSConfig,
                         refreshPeriod: Duration,
                         targets: Seq[TargetConfig],
                         outputDir: Path,
                         outputFormat: OutputFormat = YAMLFormat
                        )

/**
  * Basic AWS settings/credentials.
  */
case class AWSConfig(accessKey: String, secretKey: String, region: String)

/**
  * Prometheus scrape target configuration.
  */
case class TargetConfig(name: String,
                        search: SearchConfig,
                        metrics: MetricsConfig)

sealed abstract trait SearchConfig {
  def mode: String
}
object SearchMode {
  val EC2 = "ec2"
  val ECS = "ecs"
  def values = Seq(EC2, ECS)
}
case class EC2SearchConfig(filters: Map[String,String] = Map.empty[String,String],
                           tags: Map[String,String] = Map.empty[String,String]
                          ) extends SearchConfig {
  final val mode = SearchMode.EC2
}
case class ECSSearchConfig(cluster: String,
                           service: String) extends SearchConfig {
  final val mode = SearchMode.ECS
}

/**
  * Dictates how the final Prometheus file configuration will be generated.
  *
  * @param ports - each host will be paired with each of the ports
  * @param hostAttribute - specifies which EC2 instance attribute will be used as the target host value
  * @param labels
  */
case class MetricsConfig(ports: Seq[Int],
                         hostAttribute: TargetHostAttribute,
                         labels: Map[String,String] = Map.empty[String,String])
