# OCSS - Docker Compose Setup

Depending on the requirements, the OCS-Stack can run in different ways to fetch configuration and optionally with a frontend for search and configuration.

- basic: Elasticsearch+Kibana with indexer-service and search-service
- local: For local development there is no need for a configservice. Simply mount the application-properties into the indexer+search services.
- cloudconfig: Adds a config-service that connects to a github repo from which it fetches yml files for indexer + search service.
  With that extension, both services are changed to wait for the config-service to start. Also they get unique application names which map to the files in the repo.
  The config-service by Spring also works with other backends. [More Infos in the Cloud Config documentation](https://docs.spring.io/spring-cloud-config/docs/current/reference/html/)
- fronted: That UI application is not just a nice search-interfacet but also the ability to modify the configuration files in a GitHub repository. Access credentials required.
  The search-UI should also work without a configuration backend, so in combination with the "local mode".

All those components and adjustment are defined into separate docker-compose files that need to be combined accordingly. 
To avoid accidental 'up' commands with the wrong files we renamed the 'docker-compose.yml' into 'docker-compose.base.yml'. 
Additional it's recommended to create a small wrapper script around docker compose with the according files to be used, like here the dc-cloudconfig.sh and dc-local.sh
For credentials use a `.env` files. As an example with all required variables listed you'll find a `_env_example` file.

For customizations to the docker compose files, feel free to add another docker-compose.custom.yml that is fetched automatically by the existing `dc-*.sh` scripts.

## GitHub Config Repo

Some remarks about the GitHub repository that contains the configuration files for indexer and search service:
- The repository name should start with "ocss-config-" to be recognized by the configuration UI
- The yml-files should be named according to the application names of the services, so 'search-service.yml' and 'search-service.yml' per default
- The repository may also contain other files, for example a `querqy_rules.txt` that is referenced with its `https://raw.githubusercontent.com/...` address


