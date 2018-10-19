Prometheus AWS Service Discovery (SD) Tool
==========================================

Introduction
-----------
This 'tool' is meant to run as a standalone application that would periodically query AWS in order to generate a
list of hosts that should be scanned by Prometheus.  The output files are written in a format that Prometheus understands.


Motivation
-----------
Prometheus does have an "EC2 SD" feature built-in.  However, it is not flexible and was
really only meant to discover ALL of the EC2 instances for a given AWS account.

Alternatively, Prometheus also has "[file_sd](https://prometheus.io/docs/guides/file-sd/)"
support wherein it periodically reads file(s) for target configurations.  This offers more flexibility for teams that need to construct a list of targets based on very specific search criteria.  The built-in "file SD" Prometheus feature is what this tool integrates with.

Configuration
-----------

The main configuration file is organized into three main sections:
* **aws** - AWS access configuration
* **output** - specify where to write files and what format to use
* **targets** - specify search targets in AWS

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
* _dir_ - output directory where to write files
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


## Building a Docker Image

The easiest way to build and run this tool is using Docker.
An official image will be published to DockerHub in the future.  For now, you will have to build the image yourself.


or example, if you have created your config file at 'config=/var/my_files/config.conf':

```
./gradlew dockerBuild -Pconfig=/var/my_files/config.conf
```

To run the Docker image in a container:
```
docker run -d mdome7/prometheus-aws-sd:1.0
```

You can also put your config file in a Docker volume and pass in the filepath as a run parameter.
For example if your config file is on the Docker host machine's `/var/tmp` folder, you can mount
the host directory and reference the config file.
```
docker run -d -v /host/directory:/container/directory  mdome7/prometheus-aws-sd:1.0  /container/directory/config.conf
```


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
