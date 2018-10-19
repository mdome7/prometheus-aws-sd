package mdome7.prometheusaws.output

/**
  *
  * @param targets
  * @param labels
  */
case class TargetGroup(targets: Seq[String],
                       labels: Map[String,String] = Map.empty)
