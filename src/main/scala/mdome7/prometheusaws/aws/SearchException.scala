package mdome7.prometheusaws.aws

class SearchException(private val message: String = "",
                      private val cause: Throwable = null) extends Exception(message, cause) {

}
