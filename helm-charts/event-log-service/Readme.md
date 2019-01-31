0.

Interesting Links to learn Kubernetes and Helm

https://github.com/janakiramm/Kubernetes-dev-env
https://thenewstack.io/tutorial-configuring-ultimate-development-environment-kubernetes/
https://github.com/helm/helm/tree/master/docs/examples
https://docs.helm.sh/chart_template_guide/
https://docs.bitnami.com/kubernetes/how-to/create-your-first-helm-chart/
https://docs.helm.sh/using_helm/#installing-helm
https://github.com/helm/helm/tree/master/docs/examples

Get your hands dirty now...

1. Start you minikube

```bash
minikube start
```

2. Connect your docker to the minikube

```bash
eval $(minikube docker-env)
```

3. Synchronize your docker version with the minikube's docker version

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

4. Build the project:

Running this command will build all the project and install
the event-log-service docker image in the minikube's docker registry.

```bash
mvn install
```

It might take a while for it to install, if it's the first time you run it.

5. Install Chart

You easily do

```bash
helm install event-log-service/
```

or with additional configurations or overrides

```bash
helm install --namespace ubirch --name event-log -f event-log-service/values.yaml --debug event-log-service/
```

6. Inspect your deployed infrastructure:

You can do

```bash
helm status event-log-service/
```

```bash
kubectl -n ubirch get pods
```

7. Delete your install

```bash
helm del --purge event-log;
```








