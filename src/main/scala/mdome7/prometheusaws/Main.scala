package mdome7.prometheusaws

import java.io.File
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.{Executors, TimeUnit}

import com.typesafe.scalalogging.LazyLogging
import mdome7.prometheusaws.aws.AWSSearchService
import mdome7.prometheusaws.config.ConfigurationLoader
import mdome7.prometheusaws.output.OutputWriter

import scala.collection.JavaConversions._
import scala.util.{Failure, Success}

object Main extends App with LazyLogging {

  if (args.length == 0) {
    System.err.println("Must supply path to config file")
    System.err.println("Usage: ")
    System.exit(1)
  }

  val configFile = new File(args(0))
  logger.info(s"Config file: ${configFile.getAbsolutePath}")

  val configuration = ConfigurationLoader.loadFile(configFile)
  logger.info(s"Refresh period: ${configuration.refreshPeriod.getSeconds}s")
  logger.info(s"Output format : ${configuration.outputFormat.code}")

  val searchService = new AWSSearchService(configuration.aws)
  val targetHandler = new TargetHandler(searchService)
  val writer = new OutputWriter(configuration.outputDir)
  val scheduler = Executors.newScheduledThreadPool(1)
  val runNumber = new AtomicInteger(0)


  val runner = new Runnable {
    override def run(): Unit = {
      val start = System.currentTimeMillis()

      logger.info(s"- START RUN #${runNumber.incrementAndGet} -")
      configuration.targets.foreach(targetConfig => {
        val targetGroup = targetHandler.performSearch(targetConfig)
        writer.write(targetConfig, targetGroup, configuration.outputFormat) match {
          case Success(outputPath) =>
            logger.info(s"\n---------- ${outputPath} -----------\n${Files.readAllLines(outputPath).mkString("\n")}\n-----------------------------------")
          case Failure(e) =>
            logger.error(s"Failed to write output: ${e.getMessage}", e)
        }
      })
      logger.info(s"- END RUN #${runNumber.get} (${(System.currentTimeMillis()-start)/1000}s) -")
    }
  }

  scheduler.scheduleAtFixedRate(runner, 10, configuration.refreshPeriod.getSeconds, TimeUnit.SECONDS)


}
