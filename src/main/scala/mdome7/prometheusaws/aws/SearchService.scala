package mdome7.prometheusaws.aws

import com.amazonaws.services.ec2.model.Instance

trait SearchService {
  def searchEC2Instances(filters: Map[String,String]): List[Instance]

  def searchEC2Hosts(filters: Map[String,String], attribute: TargetHostAttribute): List[String]

  def searchECSHosts(clusterName: String, serviceName: String, attribute: TargetHostAttribute): List[String]
}