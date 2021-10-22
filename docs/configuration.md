# Configuration

Configuration is essential at OCSS, because it controls the translation of the simple OCS-API to the complex Elasticsearch API.

Per default the standard Spring mechanics are used to provide the configuration for each individual index. That standard is quiet powerful, because you can enable [Spring Cloud Config](https://spring.io/projects/spring-cloud-config) that comes with a lot of different features and supported backends.

If that's not suitable for your usecase (because for example you want to integrate OCSS configuration into your own backend), it is also possible to customize the configuration retrieval by adding implementations for [IndexerConfigurationProvider](javadoc.html#apidocs/de/cxp/ocs/spi/indexer/IndexerConfigurationProvider.html) and [SearchConfigurationProvider](javadoc.html#apidocs/de/cxp/ocs/config/SearchConfiguration.html). They are requested whenever the according configuration needs to be loaded.
At the [Javadocs](javadoc.html#apidoc/index.html) you will find all information about the exiting configuration possiblities. All those settings must be provided by the according implementation.

For this documentation the Spring yaml configuration is used, to explain the different settings.


## Indexer

All configuration are prefixed with 'ocs' - or in yaml actually subordniated to that prefix. In these examples it will only be listed once in the first example.

### Connection Configuration

The connection to Elasticsearch must be part of the Spring application configuration. 
```yaml
ocs:
  connection-configuration:
    hosts: http://localhost:9200
    # optional if auth is necessary
    auth: "username:password"
```
---

### Plugin Configuration

With 'disabledPlugins' and 'preferedPlugins' you can specify plugins to ignore and which plugins to prefer in case several plugin classes are available for the same service.
All classes have to be specified with their full canonical names.

```yaml
  disabledPlugins:
    -  my.fancy.ExprimentalPlugin
  preferedPlugins:
    "[de.cxp.ocs.spi.indexer.IndexerConfigurationProvider]": "my.fancy.IndexConfigurationProviderV2"
```
---

### Default and Specific Index Configuration

All the following index specific configuration can be defined per index or once as 'default-index-config'. 
The default configuration will only be used, if there is no index specific configuration.

```yaml
  default-index-config:
    ...
  index-config:
    my-index-1:
      ...
    my-other-index:
      ...
```
---

### Data Processor Configuration

With the `data-processor-configuration` you can list the data-processors that should be used to transform data.
These can be standard processors shipped with OCSS or custom data-processors.

For data-processors that expect some configuration, it can be specified as key-value map below a key with the processor's classname.
Check the [java-doc of the data-processors](javadoc.html#de/cxp/ocs/preprocessor/impl/package-summary.html) about the configuration details.

```yaml
    data-processor-configuration:
      processors:
        # some data processors don't need any configuration
        - ExtractCategoryLevelDataProcessor
        - FlagFieldDataProcessor
      configuration:
        # for a data-processors configuration
        # specify the full canonical name as config key and
        # below it the required settings as key-value map
        "[de.cxp.ocs.preprocessor.impl.FlagFieldDataProcessor]":
          group_1_brand: "Fancy Brand"
          group_1_brand_match: 1
          group_1_noMatch: 0
          group_1_destination: "myBrand"
```
---

### Index Settings

These settings are applied to the according Elasticsearch index after the indexation process. 
They configure how the index data should be scaled (replicated) and how fast data updates should be visible.
[Check the Elasticsearch docs](https://www.elastic.co/guide/en/elasticsearch/reference/7.15/index-modules.html#dynamic-index-settings) about the details.

```yaml
    index-settings:
      replica-count: 2
      refresh-interval: 10s
```
---

### Field Configuration

It's required for the indexer to know which data fields should be indexed in which way. [Learn more about it at the Indexer docs](/indexer_service.html).

This config is split into two part: the specific fields and the dynamic fields.
The specific fields map on an explicit list of data source fields where instead the dynamic fields can use wildcard matching or type matching to map a certain data field. 
You can think of dynamic fields as some kind of templates, because internally each match produces a specific field configuration.

Dynamic fields are checked in the order they are defined and only in case no specific field was found.

```yaml
    field-configuration:
      # explicit fields must have a unique field name,
      # that's why they are defined in a map
      # Unfortunately the name needs to be defined twice
      # but this is also validated internally
      fields:
        myfield:
          name: ...
          type: ...
          ...
        myfield2:
          ...
      # this is a list of fields, where the order 
      # of the defined fields matter.
      dynamic-fields:
        - name: ...
          type: ...
          ...
```
---

Each single field has the following properites:

- `name`: required unique value
  
  Data fields with that name will be processed according to that field configuration.

  *Special case in the context of dynamic fields*: if the name is set to `attribute`, that dynamic field is only used for fields indexed as `Attribute` values.
  Other then that, the name is ignored for dynamic fields, so it can be used for transparently label the dynamic fields.

- `soure-names`: optional list of fields that match a data field.

  With that setting a data field with the 'name' must not be present inside the data.
  The contents of the data fields will be assigned to that field if they match one of the source names. This basically enables you to rename data fields.

  The same source name can appear several times at different fields. In that case the content of that data field will be put into several data fields.
  
  If several data fields match on the same field, their content will be appended to that field or rather be put into an array with several values.

  Special case in the context of dynamic fields: The names are used as regular expressions. This way you can also create a "catch-all" field.

- `type`: Can be one of the following values:
  - `string`: default
  - `number`: data must be parsable as number
  - `category`: a field with this type will be used to handle the 'categories' property inside the indexed data.
  - `id`: This is only used to identify variants inside master-variant documents for partial updates.
  - `combi`: (deprecated) used to join the values of multiple data fields into one string rather than an array. 
    It also removes duplicate values. This should rather be extracted into a data-processor.

- `field-level`: Can be set to 'master' (which is the default), 'variant' or 'both'. 
  It is used a criterion whether a data field should be indexed at that partical master/variant level or not.

- `usage`: List of one or more of the following usage declarations. If no usage is specified, that field won't be indexed at all.
  - `search`: field will be indexed for search
  - `result`: field will be part of the result hits
  - `sort`: field will be indexed to be provided as sort option
  - `facet`: field will be indexed to generate facets. Depending on the `type` these values are put into different "facet-buckets".
  - `score`: field must be of type `number` or a string with a [supported date format](https://www.elastic.co/guide/en/elasticsearch/reference/current/date.html)

- `search-content-prefix`: optional string value, that will be prefixed to every data field

Have a look on the "[preset configuration](https://github.com/CommerceExperts/open-commerce-search/blob/master/indexer-service/src/main/resources/application-preset.yml)" for a full example of the index configuration.

## Search Service

To understand the search service configuration (and not duplicate that information), it is recommended to read the [Configuration Paragraph of the Search Service](/search_service.html#configuration) first.

Here only the configuration "language" is documented, not all details of the resulting behaviour.

As an example we use the Spring yaml configuration style to describe the settings. There all settings are prefixed with `ocs`. See the [preset configuration](https://github.com/CommerceExperts/open-commerce-search/blob/master/search-service/src/main/resources/application-preset.yml) for an full example.

### Connection and Plugin Configuration

These settings are identical to the one for the [Indexer service](#connection-configuration).


### Default and Specific Tenant Configuration

It is possible to have a default configuration, that is used for all tenants where no specific configuration exists. 
Additionately the tenant's search configuration has the feature to reference parts of the default configuration. This way you can reuse certain configuration blocks.

```yaml
ocs:
  default-tenant-config:
    ...
  tenant-config:
    my-tenant:
      # there can be different tenant configuration for the same index
      index-name: "my-index"
      ...
```
---

TBD...

## Suggest Service
