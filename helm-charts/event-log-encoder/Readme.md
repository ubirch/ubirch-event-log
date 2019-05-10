# Event Log Service Helm Chart

These steps will help in testing the helm chart of the event-log service.
It assumes a running instance of cassandra and kafka

### Start you minikube

```bash
minikube start
```

also in case you minikube gets messed up you can always delete it

```bash
minikube delete
```

### Connect your docker to the minikube

```bash
eval $(minikube docker-env)
```

### Synchronize your docker version with the minikube's docker version (OPTIONAL)

It is recommended to install [dvm](https://howtowhale.github.io/dvm/install.html),
so that you can control your docker versions easily.

To synchronize the docker version - if needed -. Do this:

Run

```bash
docker version
```

if you get an error in the server section, you should synchronize, if versions are similiar,
it should be OK. However if you'd like to synchronize them, do this:

```bash
dvm use MINIKUBE VERSION
```

You're done.

### Build the project:

Running this command will build all the project and install
the event-log-service docker image in the minikube's docker registry.

```bash
mvn install
```

It might take a while for it to install, if it's the first time you run it.

Sometimes it is useful not run tests

```bash
mvn install -DskipTests
```

### Install Chart

You easily do from the ubirch-event-log/helm-charts folder

```bash
helm install event-log-service/
```

or with additional configurations or overrides

```bash
helm install --namespace ubirch --name event-log -f event-log-service/values.yaml --debug event-log-service/
```

if you would like to simply parse the files into the final template

```bash
helm install --namespace ubirch --name event-log -f event-log-service/values.yaml --debug --dry-run event-log-service/
```

### Inspect your deployed infrastructure:

You can do

```bash
helm status event-log
```

```bash
kubectl -n ubirch get pods
```

or even friendlier

```bash
minikube dashboard
```

on the dashboard, you can inspect the namespace "ubirch" or the one you installed your helm with and
inspect deployments, pods, services, etc.


### Delete your install

```bash
helm del --purge event-log;
```

### Notes

The values.yaml has been connected to the build process so that when it is compiled or packaged
the image version gets updated automatically.

### References

* https://github.com/janakiramm/Kubernetes-dev-env
* https://thenewstack.io/tutorial-configuring-ultimate-development-environment-kubernetes/
* https://github.com/helm/helm/tree/master/docs/examples
* https://docs.helm.sh/chart_template_guide/
* https://docs.bitnami.com/kubernetes/how-to/create-your-first-helm-chart/
* https://docs.helm.sh/using_helm/#installing-helm
* https://github.com/helm/helm/tree/master/docs/examples








