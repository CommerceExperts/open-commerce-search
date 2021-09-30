# Configuration

Configuration is essential at OCSS, because it controls the translation of the simple OCS-API to the complex Elasticsearch API.
Per default the standard Spring mechanics are used to provide the configuration for each individual index. That standard is quiet powerful, because you can enable [Spring Cloud Config](https://spring.io/projects/spring-cloud-config) that comes with a lot of different features and supported backends.

If that's not suitable for your usecase (because for example you want to integrate OCSS configuration into your own backend), it is also possible to customize the configuration retrieval by adding implementations for `IndexerConfigurationProvider` and `SearchConfigurationProvider`. They are requested whenever the according configuration is necessary.

At the according [javadoc](apidoc/index.html) you will find all information about the exiting configuration possiblities.

## Indexer

The [Index configuration](apidocs/de/cxp/ocs/spi/indexer/IndexerConfigurationProvider.html) contains the following parts:

- [IndexSettings](apidocs/de/cxp/ocs/config/IndexSettings.html)
- [FieldConfiguration](apidocs/de/cxp/ocs/config/FieldConfiguration.html)
- Optional [DataProcessorConfiguration](apidocs/de/cxp/ocs/config/DataProcessorConfiguration.html)

## Search Service

The [Search Configuration](apidocs/de/cxp/ocs/config/SearchConfiguration.html) contains the following parts:

- TBD...

## Suggest Service
