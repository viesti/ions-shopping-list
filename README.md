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
