# ion-shopping-list

Playground for Datomic IONS

# Development

Given a `[metosin-sandbox]` profile in `.aws/credentials`, push and deploy code via

```clojure
./repl.sh
Clojure 1.10.0
user=> (push)
Downloading: com/datomic/java-io/0.1.11/java-io-0.1.11.pom...
user=> (deploy)
{:execution-arn arn:aws:states...
user=> (deploy-status *1)
{:deploy-status RUNNING, :code-deploy-status RUNNING}
{:execution-arn arn:aws:states...
```

To tail logs:

```sh
brew install awslogs # for https://github.com/jorgebastida/awslogs
./tail.sh
datomic-ions-demo ions-demo-ions-demo-compute-i-0fc000d5262fb903...
```
