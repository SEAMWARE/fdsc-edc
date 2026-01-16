## Prepare

Setup the test cluster:

```shell
# from the root project
mvn clean deploy -Plocal
```

Create data and prepare config:

```shell
./prepare.sh
```

## Run tck

```shell
docker run --rm --name dsp-tck \
--network host \
-p "7083:7083" \
--mount type=bind,source=$(pwd)/config/tck.config,target=/etc/tck/config.properties  \
local-tck
```

## Negotiation

Catalog request

```shell
curl  -X POST \
  'http://localhost:8081/api/dsp/2025-1/catalog/request' \
  --header 'Accept: */*' \
  --header 'User-Agent: Thunder Client (https://www.thunderclient.com)' \
  --header 'Authorization: Bearer {"id":"TCK_PARTICIPANT"}' \
  --header 'Content-Type: application/json' \
  --data-raw '
{
  "@context":  "https://w3id.org/dspace/2025/1/context.jsonld",
  "@type": "dspace:CatalogRequestMessage",
  "dspace:filter": {}
}'
```

Consumer requests offer:

```shell
curl -X POST \
  'http://localhost:8081/api/dsp/2025-1/negotiations/request' \
  --header 'Accept: */*' \
  --header 'User-Agent: Thunder Client (https://www.thunderclient.com)' \
  --header 'Authorization: Bearer {"id":"TCK_PARTICIPANT"}' \
  --header 'Content-Type: application/json' \
  --data-raw '
{
   "@context": "https://w3id.org/dspace/2024/1/context.json",
  "@type": "dspace:ContractRequestMessage",
  "dspace:consumerPid": "urn:uuid:32541fe6-c580-409e-85a8-8a9a32fbe833",
  "dspace:offer": {
    "@type": "odrl:Offer",
    "@id": "...",
    "target": "urn:uuid:3dd1add8-4d2d-569e-d634-8394a8836a88"
  },
  "dspace:callbackAddress": "https://..."
}'
```


