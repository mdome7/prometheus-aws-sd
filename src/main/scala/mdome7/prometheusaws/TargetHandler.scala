package mdome7.prometheusaws

import com.typesafe.scalalogging.LazyLogging
import mdome7.prometheusaws.aws.AWSSearchService
import mdome7.prometheusaws.config.{EC2SearchConfig, ECSSearchConfig, TargetConfig}
import mdome7.prometheusaws.aws.TargetHostAttribute
import mdome7.prometheusaws.output.TargetGroup

import scala.collection.mutable

/**
  * Facade for processing the TargetConfig and generating the TargetGroup
  * @param searchService
  */
class TargetHandler(searchService: AWSSearchService) extends LazyLogging {

  def performSearch(target: TargetConfig): TargetGroup = {
    logger.info(s"""Processing target "${target.name}".""")
    val hosts = target.search match {
      case ec2: EC2SearchConfig => searchEC2(ec2, target.metrics.hostAttribute)
      case ecs: ECSSearchConfig => searchECS(ecs, target.metrics.hostAttribute)
    }
    val targets = hosts.map(h => target.metrics.ports.map(p => s"${h}:${p}")).flatMap(x =>x)
    TargetGroup(targets, target.metrics.labels)
  }

  def searchEC2(search: EC2SearchConfig, hostAttribute: TargetHostAttribute): Seq[String] = {
    val finalFilters = mutable.HashMap.empty[String,String]
    finalFilters ++= search.filters
    // searching on "tags" is actually done the same as regular filters EC2 with the only
    // difference being the "tag:" prefix added to the filter name
    search.tags.foreach({ case (tag, value) => finalFilters += s"tag:${tag}" -> value})

    searchService.searchEC2Hosts(finalFilters.toMap, hostAttribute)
  }

  def searchECS(search: ECSSearchConfig, hostAttribute: TargetHostAttribute): Seq[String] =
    searchService.searchECSHosts(search.cluster, search.service, hostAttribute)

}