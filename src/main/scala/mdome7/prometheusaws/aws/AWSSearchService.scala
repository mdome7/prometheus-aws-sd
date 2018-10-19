package mdome7.prometheusaws.aws

import com.amazonaws.auth.{AWSStaticCredentialsProvider, BasicAWSCredentials}
import com.amazonaws.services.ec2.AmazonEC2ClientBuilder
import com.amazonaws.services.ec2.model.{DescribeInstancesRequest, DescribeNetworkInterfacesRequest, Filter, Instance}
import com.amazonaws.services.ecs.AmazonECSClientBuilder
import com.amazonaws.services.ecs.model._
import com.typesafe.scalalogging.LazyLogging
import mdome7.prometheusaws.config.AWSConfig
import mdome7.prometheusaws.aws.AWSSearchService._

import scala.collection.JavaConversions._
import scala.collection.mutable.ListBuffer

class AWSSearchService(config: AWSConfig) extends SearchService with LazyLogging {

  private val credsProvider = new AWSStaticCredentialsProvider(new BasicAWSCredentials(config.accessKey, config.secretKey))
  private val ec2 = AmazonEC2ClientBuilder.standard().withCredentials(credsProvider).withRegion(config.region).build()
  private val ecs = AmazonECSClientBuilder.standard().withCredentials(credsProvider).withRegion(config.region).build()

  override def searchEC2Instances(filters: Map[String,String]): List[Instance] = {
    var request = new DescribeInstancesRequest().withFilters(mapToFilters(filters))
    searchEC2Instances(request)
  }

  override def searchEC2Hosts(filters: Map[String,String], targetAttribute: TargetHostAttribute): List[String] = {
    val request = new DescribeInstancesRequest().withFilters(mapToFilters(filters))
    getInstanceHostAttributes(request, targetAttribute)
  }

  override def searchECSHosts(clusterName: String, serviceName: String, targetAttribute: TargetHostAttribute): List[String] = {
    logger.debug(s"Looking for tasks under cluster=${clusterName}, service=${serviceName}...")
    val hosts = ListBuffer.empty[String]

    val request = new ListTasksRequest().withCluster(clusterName).withServiceName(serviceName)
    val result = ecs.listTasks(request)

    if (!result.getTaskArns.isEmpty) {
      val tasksRequest = new DescribeTasksRequest().withCluster(clusterName).withTasks(result.getTaskArns: _*)

      val tasksResult = ecs.describeTasks(tasksRequest)

      if (!tasksResult.getFailures.isEmpty) {
        throw new SearchException(s"Failed to describe tasks - ${tasksResult.getFailures.map(_.getReason).mkString(" | ")}")
      }

      val containers = ListBuffer.empty[String]
      val networkInterfaces = ListBuffer.empty[String]

      tasksResult.getTasks.iterator()
        .filter(t => t.getDesiredStatus == AWSSearchService.TASK_ACTIVE_STATUS && t.getLastStatus == AWSSearchService.TASK_ACTIVE_STATUS)
        .foreach(t => t.getLaunchType match  {
          case TaskType.FARGATE =>
            t.getAttachments.foreach(_.getDetails
              .filter(_.getName == "networkInterfaceId")
              .foreach(kvp => networkInterfaces += kvp.getValue)
            )
          case TaskType.EC2 =>
            containers += t.getContainerInstanceArn
          case _ =>
            logger.warn(s"Task ${t.getTaskArn} has unexpected launch type ${t.getLaunchType} - skipping")
        })

      if (networkInterfaces.nonEmpty) hosts ++= getNetworkInterfaceHostAttributes(networkInterfaces, targetAttribute)
      if (containers.nonEmpty) hosts ++= getContainerHostAttributes(clusterName, containers, targetAttribute)
    }
    hosts.toList
  }

  private def getContainerHostAttributes(clusterName: String, containers: Seq[String], targetAttribute: TargetHostAttribute): List[String] = {
    logger.debug(s"Looking for containers (${containers.size}): ${containers}")
    val containerRequest = new DescribeContainerInstancesRequest()
      .withCluster(clusterName)
      .withContainerInstances(containers)
    val result = ecs.describeContainerInstances(containerRequest)
    val instanceIds = result.getContainerInstances.map(_.getEc2InstanceId)

    logger.info(s"Container search found ${instanceIds.size} instances for containers...")
    val instanceRequest = new DescribeInstancesRequest().withInstanceIds(instanceIds)
    getInstanceHostAttributes(instanceRequest, targetAttribute)
  }


  private def getInstanceHostAttributes(request: DescribeInstancesRequest, targetAttribute: TargetHostAttribute): List[String] = {
    val instances = searchEC2Instances(request)
    instances.map(EntityAttributeUtil.extractHostAttribute(_, targetAttribute))
  }

  private def getNetworkInterfaceHostAttributes(networkInterfaceIds: Seq[String], targetAttribute: TargetHostAttribute): List[String] = {
    logger.debug(s"Looking for network interface IDs (${networkInterfaceIds.size}): ${networkInterfaceIds}")
    val request = new DescribeNetworkInterfacesRequest()
        .withNetworkInterfaceIds(networkInterfaceIds)
    val result = ec2.describeNetworkInterfaces(request)
    result.getNetworkInterfaces.map(EntityAttributeUtil.extractHostAttribute(_, targetAttribute)).toList
  }

  private def searchEC2Instances(req: DescribeInstancesRequest): List[Instance] = {
    var request = req
    val result = ListBuffer.empty[Instance]
    do {
      val instances = ec2.describeInstances(request)

      val reservations = instances.getReservations
      reservations.foreach(r =>
        result ++= r.getInstances
      )

      if (instances.getNextToken != null) {
        logger.info(s"More instances need to be retrieved (nextToken=${instances.getNextToken})...")
        request = new DescribeInstancesRequest().withNextToken(instances.getNextToken)
      }

    } while (request.getNextToken != null)

    logger.info(s"Instance search found ${result.size} EC2 instances")
    result.toList
  }

}

object AWSSearchService {
  val CONTAINER_ACTIVE_STATUS = "ACTIVE"
  val TASK_ACTIVE_STATUS = "RUNNING"

  object TaskType {
    val EC2 = "EC2"
    val FARGATE = "FARGATE"
  }

  def mapToFilters(filters: Map[String,String]): Seq[Filter] =
    filters.map( { case(key, value) => {
      new Filter(key, List(value))
    }}).toSeq
}