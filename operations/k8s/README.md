# Deploy open-commerce-search on kubernetes
In this folder you can find the kustomize template to generate the need deploymmentfiles for deploying the open-commerce-stack

## Requirements
- Install Elasticsarch using the official documentation:
https://www.elastic.co/guide/en/cloud-on-k8s/current/k8s-quickstart.html
- Install Kustomize using the official documentation: https://kubectl.docs.kubernetes.io/installation/kustomize/

## Preparation

### Create namespace
This namespace should match with the namespace which is set in the [base/kustomization.yaml](base/kustomization.yaml).
```
kubectl create namespace ocs-stack
```

### Prepare ingress and auth
The following changes have to be made, to make the services work like expected:

- To enable SSL on LoadBalancer level, adjust [base/api-ingress.yaml](base/api-ingress.yaml) by adding the required annotations for your cloud provider

- In case a specific domain is used and should be considered by the ingress rules, add it to [base/api-ingress.yaml](base/api-ingress.yaml)

- By default, basic authentication is configured for the APIs. To add your credentials, the following steps have to be done:
```
    htpasswd -c auth your-ocs-user
    kubectl create secret generic ingress-basic-auth --from-file=auth
```

## Deploy
```
kustomize build base | kubectl apply -f -
```

## Customize deployment
If you want to create an customized deployment, just create an kustomize overlay (https://kubectl.docs.kubernetes.io/references/kustomize/glossary/#overlay) with your adjusted settings under the folder 
[overlays](overlays). Then you can deploy it like the following example:
```
kustomize build overlays/example | kubectl apply -f -
```
The files under [overlays](overlays) would not be commited into git.
