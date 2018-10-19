package mdome7.prometheusaws.config

class InvalidConfigException(private val message: String = "",
                             private val cause: Throwable = null) extends Exception(message, cause)