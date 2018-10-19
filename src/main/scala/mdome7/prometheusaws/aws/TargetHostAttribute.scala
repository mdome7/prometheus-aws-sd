package mdome7.prometheusaws.aws

/**
  * Used to specify what attribute of the EC2 instance should be used
  * when generating the host part of the targets.
  */
sealed abstract class TargetHostAttribute(val code: String)

object TargetHostAttribute {
  def values: Seq[TargetHostAttribute] = Seq(PrivateDnsName, PrivateIpAddress, PublicDnsName, PublicIpAddress)
  def fromString(str: String): Option[TargetHostAttribute] =
    values.find(_.code == str)

  case object PrivateDnsName extends TargetHostAttribute("private-dns-name")
  case object PrivateIpAddress extends TargetHostAttribute("private-ip-address")
  case object PublicDnsName extends TargetHostAttribute("public-dns-name")
  case object PublicIpAddress extends TargetHostAttribute("public-ip-address")
}
