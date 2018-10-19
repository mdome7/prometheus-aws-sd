Prometheus AWS Service Discovery (SD) Tool
==========================================

Introduction
-----------
This 'tool' is meant to run as a standalone application that would periodically query AWS in order to generate a
list of hosts that should be scanned by Prometheus.  The output files are written in a format that Prometheus understands.


Motivation
-----------
In today's world, dev teams take advantage of many features afforded by cloud environments like AWS.
Application deployments tend to happen more frequently.  And the applications themselves are likely to change
locations (e.g. host machines, IP addresses) just as often due to regular host rotations, auto-scaling,
or utilization of AWS ECS ([Elastic Container Service](https://aws.amazon.com/ecs/)).

This can be a challenge for teams that use Prometheus for metrics since it uses a "pull" model wherein
Prometheus has to know where to scrape the metrics.  Prometheus configurations have to be updated
when applications move around.

Prometheus does have service discovery (sd) options built-in that helps it find targets dynamically.
This includes _[ec2_sd](https://prometheus.io/docs/prometheus/latest/configuration/configuration/#%3Cec2_sd_config%3E)_ for AWS EC2 support.
However, it is not very flexible and was originally only able to list and scrape all EC2 instances for a given AWS account.
They have since updated it and added *filters* so that you can specify which EC2 instances to scrape.
However, it still has some limitations (single scrape port, clunky private/public IP address targeting)
and also does not support discovery for AWS ECS.

Alternatively, Prometheus has _[file_sd](https://prometheus.io/docs/guides/file-sd/)_
support wherein it periodically reads files for target configurations.
This offers more flexibility for teams that can use any number of methods to dynamically
construct a list of scrape targets using very specific search criteria.
This built-in _file_sd_ Prometheus is what this tool integrates with.

This tool provides a more flexible way to search for EC2 instances and ECS tasks, and then
writes the target information into files that are read by Prometheus using _file_sd_.
(NOTE: _[AWS EKS](https://aws.amazon.com/eks) is currently not supported_)  

Configuration
-----------

The main configuration file is organized into three main sections:
* **aws** - specifies how to AWS access configuration
* **output** - specifies where to write the output files and what format to use
* **targets** - specifies one ore more search entries used to discover targets in AWS

The file itself uses [HOCON (Human-Optimized Config Object Notation)](https://github.com/lightbend/config/blob/master/HOCON.md)
format, which is a superset of JSON.  You can format it in JSON and it will
still be a valid HOCON file and will be parsed correctly.


### Config Parameters

_refresh-period_ - specifies how often to poll AWS
                   (Uses the [HOCON duration format](https://github.com/lightbend/config/blob/master/HOCON.md#duration-format)
                    e.g. "120s"=2 minutes, "2m"=2 minutes, "1h"=1 hour)

**AWS Section**
* _access-key_ - AWS access key
* _secret-key_ - AWS secret key
* _region_ - AWS region


**Output Section**
* _dir_ - output directory where to write files (NOTE: _Your Prometheus instance needs to have access to this directory as well._)
* _format_ - "yaml" or "json" - both formats can be read by Prometheus file_sd

**Targets Section**

The _targets_ section is a list of search config entries with the following attributes:

* _name_ - used to identify each search config.  Also used for the output filename.
* _search_ - specify how to "find" the AWS hosts
  * _mode_ - "ec2" or "ecs"
  * _filters_ - (ec2-only) search by instance attributes.
              For a list of possible filter keys [DescribeInstances documentation](https://docs.aws.amazon.com/AWSEC2/latest/APIReference/API_DescribeInstances.html).
  * _tags_ - (ec2-only) search by tags assigned to EC2 instances
  * _cluster_ - (ecs-only) ECS cluster name
  * _service_ - (ecs-only) ECS service name within the specified _cluster_

* _metrics_ - specify how to expose the metrics endpoint that Prometheus will scrape
  * _host-attribute_ - one of "**public-dns-name**", "**public-ip-address**", "**private-dns-name**", "**private-ip-address**".
                       Depending on how your network is configured in AWS,
                       Prometheus may only be able to reach your endpoints using private or public IP addresses or hostnames.
  * _ports_ - a list of port numbers for Prometheus to scrape on each target found.
              You may have an application exposing metrics on port 80 while also running
              [Prometheus Node Exporter](https://github.com/prometheus/node_exporter)
              which exposes metrics on port 9100.

### Sample Config File
*/var/my_files/config.conf*
```
refresh-period: 120s

aws {
  access-key: "ABC123"
  secret-key: "DEF456"
  region: us-east-1
}

output {
  dir: /var/tmp/output
  format: yaml
}

targets: [
  {
    name: target-1-ec2-no-labels
    search: {
      mode: ec2
      tags: {
        name: my-cool-app
      }
    }
    metrics: {
      host-attribute: public-dns-name
      ports: [80]
    }
  },
  {
    name: target-2-ecs-test
    search: {
      mode: ecs
      cluster: ecs-test-cluster
      service: ecs-test-service
    }
    metrics: {
      host-attribute: public-ip-address
      ports: [80, 8080]
      labels: {
        env: test
      }
    }
  },
  {
    name: target-3-ecs-prod
    search: {
      mode: ecs
      cluster: ecs-prod-cluster
      service: ecs-prod-service
    }
    metrics: {
      host-attribute: private-ip-address
      ports: [80, 9100]
      labels: {
        env: prod
      }
    }
  }
]

```


### Output Files

Below are sample output files based on the above configuration.

*/var/tmp/output/target-1-ec2-no-labels.yml*
```yaml
- targets:
    - ec2-111-111-111-111.compute-1.amazonaws.com:80
    - ec2-222-222-222-222.compute-1.amazonaws.com:80
```

*/var/tmp/output/target-2-ecs-test.yml*
```yaml
- targets:
    - 111.111.111.111:80
    - 111.111.111.111:8080
  labels:
    env: test
```

*/var/tmp/output/target-3-ecs-prod.yml*
```yaml
- targets:
    - 10.10.10.10:80
    - 10.10.10.10:9100
    - 10.20.20.20:80
    - 10.20.20.20:9100
  labels:
    env: prod
```


### Prometheus Config File

Below is a sample Prometheus config file.

*prometheus.yml*
```yaml
scrape_configs:
- job_name: 'target1'
  file_sd_configs:
  - files:
    - '/var/tmp/output/target-1-ec2-no-labels.yml'
    
- job_name: 'target2'
  file_sd_configs:
  - files:
    - '/var/tmp/output/target-2-ecs-test.yml'
    
- job_name: 'target3'
  file_sd_configs:
  - files:
    - '/var/tmp/output/target-3-ecs-prod.yaml'
```

Prometheus will periodically read the files specified in the configuration
(which are in turn updated periodically by this application).


Running the Application
-----------

### Building and Running in Docker

The easiest way to build and run this tool is using Docker.
An official image will be published to DockerHub in the future.  For now, you will have to build the image yourself.


For example, if you placed your config file at `/var/my_files/config.conf`:

```
./gradlew dockerBuild -Pconfig=/var/my_files/config.conf
```

To run the Docker image in a container:
```
docker run -d mdome7/prometheus-aws-sd:1.0
```

You can also put your config file in a Docker volume and pass in the filepath as a run argument.
For example if your config file is on the Docker host machine's `/var/tmp` folder, you can mount
the host directory and reference the config file.
```
docker run -d -v /host/directory:/container/directory  mdome7/prometheus-aws-sd:1.0  /container/directory/config.conf
```

### Using the Distribution Tarball or Zip file

You can package up the application into a tarball (*.tar) or zip fie (*.zip) by using the Gradle
[distribution plugin](https://docs.gradle.org/current/userguide/distribution_plugin.html) and associated tasks:

```
# Create a tarball
./gradlew distTar

# or create a zip file
./gradlew distZip
```

You should see the created tarball/zip under the `build/distributions/` directory which 
you can then move anywhere you want to run the application.

Unpack the tarball/zip and run the executable while specifying the path to your configuration file
```
tar -xf prometheus-aws-sd-1.0.tar
prometheus-aws-sd-1.0/bin/prometheus-aws-sd  path/to/my/config.conf
```
