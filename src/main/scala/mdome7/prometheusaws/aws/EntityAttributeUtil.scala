package mdome7.prometheusaws.aws

import com.amazonaws.services.ec2.model.{Instance, NetworkInterface}
import mdome7.prometheusaws.aws.TargetHostAttribute._

/**
  * Utility class to retrieve relevant attributes from AWS entities.
  */
object EntityAttributeUtil {

  implicit class AttributeBlankChecker(attrValue: String) {
    def orIfBlankThrowExeption(resourceArn: String, attributeName: String): String =
      if (attrValue == null || attrValue.trim.isEmpty)
        throw new AttributeBlankException(resourceArn, attributeName, s"${attributeName} is blank")
      else
        attrValue
  }


  def extractHostAttribute(i: Instance, attribute: TargetHostAttribute): String = attribute match {
    case PrivateDnsName => i.getPrivateDnsName.orIfBlankThrowExeption(i.getInstanceId, "privateDnsName")
    case PrivateIpAddress => i.getPrivateIpAddress.orIfBlankThrowExeption(i.getInstanceId, "privateIpAddress")
    case PublicDnsName => i.getPublicDnsName.orIfBlankThrowExeption(i.getInstanceId, "publicDnsName")
    case PublicIpAddress => i.getPublicIpAddress.orIfBlankThrowExeption(i.getInstanceId, "publicIpAddress")
  }

  def extractHostAttribute(ni: NetworkInterface, attribute: TargetHostAttribute): String = attribute match {
    case PrivateDnsName => ni.getPrivateDnsName.orIfBlankThrowExeption(ni.getNetworkInterfaceId, "privateDnsName")
    case PrivateIpAddress => ni.getPrivateIpAddress.orIfBlankThrowExeption(ni.getNetworkInterfaceId, "privateIpAddress")
    case PublicDnsName => ni.getAssociation.getPublicDnsName.orIfBlankThrowExeption(ni.getNetworkInterfaceId, "association.publicDnsName")
    case PublicIpAddress => ni.getAssociation.getPublicIp.orIfBlankThrowExeption(ni.getNetworkInterfaceId, "association.publicIp")
  }
}
