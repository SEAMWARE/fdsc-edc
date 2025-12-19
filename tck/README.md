## Prepare

Setup the test cluster:

```shell
# from the root project
mvn clean deploy -Plocal
```

Create data and prepar config:

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
