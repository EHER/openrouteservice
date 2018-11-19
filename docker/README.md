# Install and run openrouteservice with docker

Installing the openrouteservice backend service with **Docker** is quite straightforward.

## Short version

### Run from Docker hub

Run latest version from [Docker hub] pointing to your data path (e.g. $PWD/docker/data/):

```bash
docker run --name openrouteservice -d -p 8080:8080 -v $PWD/docker/data/:/usr/local/tomcat/data/ giscience/openrouteservice
```

## Long version

Please clone the repository (downloading the archive and running docker is currently not supported) and run the following commands in the root of the project:

```bash
docker build -t openrouteservice .
docker run --name openrouteservice -d -p 8080:8080 -v $PWD/docker/data/:/usr/local/tomcat/data/ openrouteservice
```

This will:

1. Build and test the openrouteservice core from the local codebase with the `docker/conf/app.config.sample` as the config file and the OpenStreetMap dataset for Heidelberg under `docker/data/` as sample data.
2. Generate the built `ors.war` file and expose it to `docker/build/` directory.
3. Launch the openrouteservice service on port `8080` within a tomcat container.

By default the service status is queryable via the `http://localhost:8080/ors/health` endpoint. When the service is ready, you will be able to request `http://localhost:8080/ors/status` for further information on the running services. If you use the default dataset you will be able to request `http://localhost:8080/ors/routes?profile=foot-walking&coordinates=8.676581,49.418204|8.692803,49.409465` for test purposes. 

### Run with your own OpenStreetMap dataset

Prepare the OSM dataset (formats supported are `.osm`, `.osm.gz`, `.osm.zip` and `.pbf`) in the `docker/data/` directory. Adapt your own `app.config` (check the sample with detailed comments [here](../openrouteservice/WebContent/WEB-INF/app.config.sample) for reference) and change the `APP_CONFIG` variable in `docker-compose.yml` to let it point to your customized `app.config`. Then, run `docker-compose up`.

It should be mentioned that if your dataset is very large, please adjust the `-Xmx` parameter of `JAVA_OPTS` in `docker-compose.yml`. According to our experience, this should be at least `180g` for the whole globe if you are planning to use 3 or more modes of transport.
