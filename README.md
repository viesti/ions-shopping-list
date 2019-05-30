# ion-shopping-list

Playground for Datomic Ions. Exposes a couple of Ions through API Gateway.

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

To tail logs (using [awslogs](https://github.com/jorgebastida/awslogs), install it first via `brew install awslogs`):

```sh
./tail.sh
datomic-ions-demo ions-demo-ions-demo-compute-i-0fc000d5262fb903...
```

For connecting to Datomic Cloud from the repl, [install a proxy](https://docs.datomic.com/cloud/getting-started/connecting.html#socks-proxy) and then run it:

```sh
./proxy.sh
```

, and the make queries:

```clojure
user> (def client (create-client))
#'user/client
user> (def connection (d/connect client {:db-name core/db-name}))
#'user/connection
user> (def db (d/db connection))
#'user/db
user> (d/q core/items-query db)
[[5774635069079618 "Maitoa" 0] [49675935342919747 "Kahvia" 1]]
```

After adding Ions labeled with `:integration :api-gateway/proxy` via `(deploy)`, update the API Gateway via

```clojure
user> (update-api-stack)
```

The above can also be used to remove routes to `:api-gateway/proxy` Ions that have been removed from `resources/datomic/ion-config.edn`.
