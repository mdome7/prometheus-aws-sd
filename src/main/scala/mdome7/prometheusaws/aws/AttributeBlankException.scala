package mdome7.prometheusaws.aws

/**
  * Throw if an attribute is blank when it was expected to be populated.
  * @param resource unique ID or ARN
  * @param attributeName
  * @param message
  * @param cause
  */
class AttributeBlankException(resource: String,
                              attributeName: String,
                              private val message: String = "",
                              private val cause: Throwable = null) extends Exception(
  if (message != "") message else s"Attribute ${attributeName} of ${resource} is blank", cause) {

}
