package mdome7.prometheusaws.config

import java.io.File
import java.time.Duration

import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.LazyLogging
import org.scalatest.{BeforeAndAfter, FunSpec, Matchers}

class ConfigurationLoaderSpec extends FunSpec with Matchers with BeforeAndAfter with LazyLogging {

  describe("ConfigurationLoader") {
    describe("parseConfiguration") {
      describe("when loading from Config") {
        val inputConfig = ConfigFactory.load("sample-input")
        val conf = ConfigurationLoader.parseConfiguration(inputConfig)
        sharedConfigurationVerifier(conf)
      }
      describe("when loading from File") {
        val file = new File("./build/resources/main/sample-input.conf")
        val conf = ConfigurationLoader.loadFile(file)
        sharedConfigurationVerifier(conf)
      }
    }
  }

  def sharedConfigurationVerifier(conf: Configuration): Unit = {
    it ("loads the refreshPeriod") {
      conf.refreshPeriod should not be (null)
      conf.refreshPeriod should be (Duration.ofMinutes(2L))
    }
    it ("loads the output file") {
      conf.outputDir should not be (null)
    }
    it ("loads the AWS creds") {
      conf.aws should not be (null)
      conf.aws.accessKey should not be (null)
      conf.aws.secretKey should not be (null)
    }
    it ("loads the targets") {
      conf.targets should have size(3)
    }
    it ("supports different search modes") {
      conf.targets.foreach(t => {
        if (t.search.mode == SearchMode.EC2) {
          t.search shouldBe a [EC2SearchConfig]
        } else {
          t.search shouldBe a [ECSSearchConfig]
        }
      })
    }
  }


}
