# Configuration

Configuration is essential at OCSS, because it controls the translation of the simple OCS-API to the complex Elasticsearch API.

Per default the standard Spring mechanics are used to provide the configuration for each individual index. That standard is quiet powerful, because you can enable [Spring Cloud Config](https://spring.io/projects/spring-cloud-config) that comes with a lot of different features and supported backends.

If that's not suitable for your use case (because for example you want to integrate OCSS configuration into your own back end), it is also possible to customize the configuration retrieval by adding implementations for [IndexerConfigurationProvider](javadoc.html#apidocs/de/cxp/ocs/spi/indexer/IndexerConfigurationProvider.html) and [SearchConfigurationProvider](javadoc.html#apidocs/de/cxp/ocs/config/SearchConfiguration.html). They are requested whenever the according configuration needs to be loaded.
At the [Java-docs](javadoc.html#apidoc/index.html) you will find all information about the exiting configuration possibilities. All those settings must be provided by the according implementation.

For this documentation the Spring yaml configuration is used, to explain the different settings.


## Indexer

All configuration are prefixed with 'ocs' - or in yaml actually subordinated to that prefix. In these examples it will only be listed once in the first example.

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

With 'disabled-plugins' and 'prefered-plugins' you can specify plugins to ignore and which plugins to prefer in case several plugin classes are available for the same service.
All classes have to be specified with their full canonical names.

```yaml
  disabled-plugins:
    -  my.fancy.ExprimentalPlugin
  prefered-plugins:
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
      plugin-configuration:
        ...
      query-processing:
        ...
      query-configs:
        ...
      rescorers:
        ...
      facet-configuration:
        ...
      sort-configs:
        ...
```
---

#### Plugin Configuration

The plugin configuration is a data map that contains custom key-value data for activated search plugins. 
Plugins are always activated in their according context, e.g. at the "query-processing" config.

As a key the full canonical class-name of the plugin must be used. The expected data depends on the plugin's implementation.

```yaml
      plugin-configuration:
        "[my.example.FancyCustomization]":
          "key1": "value1"
          "key2": "value2"
        # real example:
        "[de.cxp.ocs.elasticsearch.query.analyzer.QuerqyQueryExpander]":
          "common_rules_url": "rules/querqy_rules.my_index.txt"
```
---

#### Query Processing

At this section the details are configured, about how a user-query is processed before the Elasticsearch query is built.

- `user-query-preprocessors`: A list of classes that may replace the 
- `user-query-analyzer`: Specify the class that should do the analyzing part. It must be an implementation of the `de.cxp.ocs.spi.search.UserQueryAnalyzer` interface. 

  Per Default the following options are available:
  - `de.cxp.ocs.elasticsearch.query.analyzer.WhitespaceAnalyzer` (default) It splits the user query by white space into terms
  - `de.cxp.ocs.elasticsearch.query.analyzer.WhitespaceWithShingles` Similar to the default analyzer but it additionally adds shingle-terms of the adjoining terms
  - `de.cxp.ocs.elasticsearch.query.analyzer.QuerqyQueryExpander` Sophisticated analyzer that uses a [Querqy Common Rewriter](https://docs.querqy.org/querqy/rewriters/common-rules.html#querqy-rewriters-common-rules) rule definition file to add filters and synonyms to the query
  

```yaml
      query-processing:
        user-query-preprocessors:
          - "my.example.FancyCustomization"
        user-query-analyzer: "de.cxp.ocs.elasticsearch.query.analyzer.QuerqyQueryExpander"
```
---


#### Query Configuration

```yaml

```
---

#### Facet Configuration

With the facet configuration you can add additional behaviour about how facets are generated. 
It contains two properties:
- `max-facets`: (default = 5). With this facet you can limit the amount of facets that should be generated. 

  A low number improves performance (and maybe even usability) because less facets are generated. 
  Each individual facet can set the value `excludeFromFacetLimit: true` to make it being generated all the time, ignoring that limit (useful for standard facets). 
  In general the most common facets for a particular result are generated, but if facets are equally common, it's up to Elasticsearch's logic which facets are returned.

- `facets`: A list of individual facet configs. Without an individual facet config, facets are generated with default behaviour.

```yaml
    facet-configuration:
      max-facets: <int>
      facets:
      - source-field: "<field-name>"
        label: "<label>"
        type: [term|hierarchical|interval|range|<custom>]
        order: <int>
        optimal-value-count: <int>
        exclude-from-facet-limit: <boolean>
        show-unselected-options: <boolean>
        is-multi-select: <boolean>
        meta-data:
          "<key>": "<value>"
      - ...
```

Each individual facet config may contain the following properties:

- `source-field`: (required) Specifies for which facet this configuration applies to. Only one facet config is allowed per field.
- `label`: (default = source-field) Defines the 'label' for that facet. Will be part of the returned `meta-data`
- `type`: Per default the type depends on the field type. This is "term" for string fields, "interval" for number fields, and "hierarchical" for category fields. 
  So basically this is only useful to set to "range" for numeric fields or in case a custom facet creator is used.
- `order`: (default = 127) Accepted are values between 0 and 127. This order value will be used to sort the facets according to it (low order values are put higher).
  If the order of two facets is the same, the one that's filtered will be preferd. If both have the same filter-status, the one with the higher result-coverage will be prefered.
- `optimal-value-count`: (default = 5) Only used for "interval" facets to specify how many interval-filter-options should be generated at the maximum (if there enough results).
- `explude-from-facet-limit`: (default = false) See `max-facets` description above
- `show-unselected-options`: (default = false) If set to "true" all possible facet values will be returned, even if one of them is used as filter. Choosing another filter-option will then toggle the selected filter.
- `is-multi-select`: (default = false) If set to "true" the behaviour is similar to `show-unselected-options` and additionally choosing another filter-option will filter the result for both of them inclusively (e.g. "blue" or "red").
- `meta-data`: (default = null) Can be used to add arbitrary data to a facet. The value is a simple string map. This is useful to add configuration values that can be considered at the implementation side. Some internal data is also exposed at that meta-data map (e.g. label and count)

---

#### Scoring Configuration

With this configuration you can influence the final scores of the result hits. This configuration actually is a mapping of the [Elasticsearch Function-Score Query](https://www.elastic.co/guide/en/elasticsearch/reference/current/query-dsl-function-score-query.html). So take the details about the configured behaviour from the Elasticsearch docs.

Options:
- `boost-mode`: (default = avg) This parameter specifies how the computed scores are combined with the generic score of Elasticsearch.
- `score-mode`: (default = avg) This parameter specifies how all the computed scores are combined together.
- `score-function`: A list of score functions.

```yaml
    scoring:
      boost-mode: [multiply|avg|sum|min|max|replace]
      score-mode: [multiply|avg|sum|min|max|first]
      score-functions:
        - field: "<field-name>"
          type: [weight|random_score|field_value_factor|script_score|decay_gauss|decay_linear|decay_exp]
          weight: <float>
          options:
            "<key>": "<value>"
        - ... 
          
```

Each function must have at least a `type` property and depending on that one or more of the following properties:

- `type`: One of `weight`, `random_score`, `field_value_factor`, `script_score`, `decay_gauss`, `decay_linear`, `decay_exp`
  This type relates to the according [score function of Elasticsearch](https://www.elastic.co/guide/en/elasticsearch/reference/current/query-dsl-function-score-query.html)
- `field`: Specifies on which score field this function should be applied on. That field must be indexed for "Score" usage. 
  This is necessary for all types but `weight` and `script_score`.
- `weight`: (default = 1) A value that is multiplied with the output of the according score function, before it is calculated into the final score
- `options`: A key-value map with different options for the different functions:
  - `USE_FOR_VARIANTS`: `<boolean>` (default = false) If set to `true`, that scoring option will also be used to score the variant records of a master among each other.
  - `RANDOM_SEED`: `<string>` Used for `random_score` function. If not set, the random function won't be deterministic and change for each request
  - `MISSING`: `<value>` (default = 0) Used for all functions that use data from a `field`. It specifies the value for a document that misses the value for the according scoring field.
  - `MODIFIER`: `[none|log|log1p|log2p|ln|ln1p|ln2p|square|sqrt|reciprocal]` (default = "none") Mathematical modifier for the data values. [See ES docs for details](https://www.elastic.co/guide/en/elasticsearch/reference/current/query-dsl-function-score-query.html#function-field-value-factor) 
  - `FACTOR`: `<float>` (default = 1) Factor (double value) that is multiplied to each field value, before the modifier is applied to it.
  - `SCRIPT_CODE`: `<string>` Specifies the required script for the `script_score` function.
  - `ORIGIN`: `<float>` Required option for the decay_* score types. 
    From [the official docs](https://www.elastic.co/guide/en/elasticsearch/reference/current/query-dsl-function-score-query.html#function-decay):
    > The point of origin used for calculating distance. Must be given as a
    > number for numeric field, date for date fields and geo point for geo
    > fields. Required for geo and numeric field. For date fields the
    > default is now. Date math (for example now-1h) is supported for
    > origin.
  - `ORIGIN`: `<float>` Required option for the decay_* score types.
    From [the official docs](https://www.elastic.co/guide/en/elasticsearch/reference/current/query-dsl-function-score-query.html#function-decay):
    > Defines the distance from origin + offset at which the computed 
    > score will equal decay parameter. For geo fields:
    > Can be defined as number+unit (1km, 12m,…). Default unit is meters.
    > For date fields: Can to be defined as a number+unit ("1h", "10d",…).
    > Default unit is milliseconds. For numeric field: Any number.
  - `ORIGIN`: `<float>` Required option for the decay_* score types.
    From [the official docs](https://www.elastic.co/guide/en/elasticsearch/reference/current/query-dsl-function-score-query.html#function-decay):
    > The decay parameter defines how documents are scored at the distance given at scale.
    > If no decay is defined, documents at the distance scale will be scored 0.5.

---


## Suggest Service
