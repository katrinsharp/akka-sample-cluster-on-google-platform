# How to run Akka Cluster on Google Cloud Platform

This is a short guide to walk you through how to deploy and run Akka Cluster based application on [Google Cloud Platform](https://cloud.google.com/).

**Note:** This guide is about how to deploy Akka application to [Google Container Engine (GKE)](https://cloud.google.com/container-engine/). If you are interested in deploying Akka application to [Google Compute Engine (GCE)](https://cloud.google.com/compute/) instead, read the following [article](https://www.lightbend.com/blog/how-to-run-akka-on-google-compute-engine).

**Background:** Google Container Engine's underlying technologies are [Docker](https://www.docker.com/) and [Kubernetes](http://kubernetes.io/). Some familiarity with those technologies is assumed.

**Creating Google Container cluster:**

Google provides an excellent documentation how to signup to their platform, create and manage Container Clusters. For the rest of this guide, you will need:

- Signup to Google Container Engine. Documentation is [here](https://cloud.google.com/container-engine/docs/quickstart).
- Create a cluster with 3 nodes (default configuration  should suffice). Documentation is [here](https://cloud.google.com/container-engine/docs/clusters/operations).
- To get familiar with Google Container Registry. Documentation is [here](https://cloud.google.com/container-registry/docs/how-to).

**Compiling and packaging sample Akka application:**

**Background:** Our sample application is inspired by [akka-sample-cluster](https://github.com/akka/akka/tree/master/akka-samples/akka-sample-cluster-scala). It has backend nodes that calculate factorial upon receiving messages from frontend nodes.

- Clone sample application: from [here](https://github.com/katrinsharp/akka-sample-cluster-on-googlecontainerengine).
- Compile and package sample components:
```
sbt backend:assembly # backend
sbt frontend:assembly # frontend
```
**Dockerizing Akka application:**

- Docker build:

  We have 3 Dockerfiles:

- `Dockerfile-java` is a file for base Docker image that contains Java installation. To build it:

  ```
  docker build -t java -f Dockerfile-java .
  ```
- `Dockerfile-backend` is file with a Backend component image. To build it:

  ```
  docker build -t akka-sample-cluster-backend -f Dockerfile-backend .
  ```
- `Dockerfile-frontend` is file with a Front component image. To build it:

  ```
  docker build -t akka-sample-cluster-frontend -f Dockerfile-frontend .
  ```
- Testing locally with Docker:

  + Running 2 backend nodes:
  ```
 docker run -d -it -e "THIS_IP=192.168.99.100" -e "AKKA_SAMPLE_SEED_IP_1=192.168.99.100" -e "AKKA_SAMPLE_SEED_PORT_1=2551" -e "AKKA_SAMPLE_SEED_IP_2=192.168.99.100" -e "AKKA_SAMPLE_SEED_PORT_2=2552" -p "2551:2551"  --name akka-sample-cluster-backend-1 akka-sample-cluster-backend java -jar akka-sample-backend.jar 2551
 docker run -d -it -e "THIS_IP=192.168.99.100" -e "AKKA_SAMPLE_SEED_IP_1=192.168.99.100" -e "AKKA_SAMPLE_SEED_PORT_1=2551" -e "AKKA_SAMPLE_SEED_IP_2=192.168.99.100" -e "AKKA_SAMPLE_SEED_PORT_2=2552" -p "2552:2552"  --name akka-sample-cluster-backend-2 akka-sample-cluster-backend java -jar akka-sample-backend.jar 2552
  ```
  + Running a frontend:
  ```
  docker run -d -it -e "THIS_IP=192.168.99.100" -e "AKKA_SAMPLE_SEED_IP_1=192.168.99.100" -e "AKKA_SAMPLE_SEED_PORT_1=2551" -e "AKKA_SAMPLE_SEED_IP_2=192.168.99.100" -e "AKKA_SAMPLE_SEED_PORT_2=2552" --name akka-sample-frontend akka-sample-cluster-frontend java -jar akka-sample-frontend.jar
  ```
  **Note:** Substitute `192.168.99.100` with IP of your Docker machine (`docker-machine env`).

  + Verifying it works: check out frontend logs (`docker logs -f <container-id>`). You should see stream of following:
  ```
  [INFO] [10/22/2016 19:04:39.132] [AkkaSampleCluster-akka.actor.default-dispatcher-19] [akka.tcp://AkkaSampleCluster@192.168.99.100:36900/user/factorialFrontend] 10! = 3628800 sender: akka.tcp://AkkaSampleCluster@192.168.99.100:2552/user/factorialBackend
  ```

**Note:** If you change the source, you will need to repackage and then rebuild relevant docker image.

**Deploy to Google Container Engine:**

- Pushing images to Google Container Engine. Documentation how to push can be found [here](https://cloud.google.com/container-registry/docs/pushing).
  ```
  docker tag akka-sample-cluster-backend gcr.io/<your-project-id>/akka-sample-cluster-backend
  docker tag akka-sample-cluster-frontend gcr.io/<your-project-id>/akka-sample-cluster-frontend
  gcloud docker push gcr.io/<your-project-id>/akka-sample-cluster-backend
  gcloud docker push gcr.io/<your-project-id>/akka-sample-cluster-frontend
  ```
**Background:** with Akka Cluster every node should know IPs/hostnames and ports of [cluster seed nodes](http://doc.akka.io/docs/akka/current/scala/cluster-usage.html#Joining_to_Seed_Nodes). Containers in Google Container Engine have dynamic IPs making it impossible to manage a list of static IPs for seed nodes. Some possible solutions are to use [etcd](https://github.com/coreos/etcd) directly or via [ConstructR](https://github.com/hseeberger/constructr) that utilizes etcd as Akka extension. However, Kubernetes also have [headless services](http://kubernetes.io/docs/user-guide/services/#headless-services). We are going to use it to expose all seed node IPs via DNS by having a headless service, which will be attached to each seed node through selector.

- Deploy headleass service:
  ```
  kubectl create -f Discovery-service.yaml
  ```
**Note:** In our case we mark all backend nodes as seed nodes, in real use case you need to mark only some of them as seed and have two different deployments.

- Deploy a sampe application components:
  + Backend:
  ```
  kubectl create -f Backend-seed.yaml
  ```
  + Frontend:
  ```
  kubectl create -f Frontend.yaml
  ```
  + Verifying it works: 
  
  Check out frontend logs (`kubectl logs -f <container-id>`). You should see stream of following:
  ```
  [INFO] [10/22/2016 20:07:43.055] [AkkaSampleCluster-akka.actor.default-dispatcher-16] [akka.tcp://AkkaSampleCluster@10.0.2.14:34018/user/factorialFrontend] 50! = 30414093201713378043612608166064768844377641568960512000000000000 sender: akka.tcp://AkkaSampleCluster@10.0.2.12:2551/user/factorialBackend
  ```

**Note** Yaml deployment files declare 2 backend nodes and 1 frontend node to be started right away. It should be sifficient to see expected results. In general, to scale up/down:
```
kubectl scale --replicas=<target-number> deployment/cluster-backend-seed
kubectl scale --replicas=<target-number> deployment/cluster-frontend
```
For more `kubectl` options such listing pods, deplyoments etc: [Kubernetes cheatsheet](http://kubernetes.io/docs/user-guide/kubectl-cheatsheet/).

## Summary

This guide shows how deploy and run Akka Cluster application on Google Container Engine. While more steps should be taken to harden it for production use, it is a successful proof-of-concept that demonstrates Akka Cluster working on Google Container Engine.