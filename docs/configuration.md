[Home](./) > [Configuration](configuration.md)

# Table of Contents

- [Configuration](#configuration)
  - [Indexer](#indexer)
    - [Connection Configuration](#connection-configuration)
    - [Plugin Configuration](#plugin-configuration)
    - [Default and Specific Index Configuration](#default-and-specific-index-configuration)
      - [Data Processor Configuration](#data-processor-configuration)
      - [Index Settings](#index-settings)
      - [Field Configuration](#field-configuration)
  - [Search Service](#search-service)
    - [Connection and Plugin Configuration](#connection-and-plugin-configuration)
    - [Default and Specific Tenant Configuration](#default-and-specific-tenant-configuration)
      - [Plugin Configuration](#plugin-configuration)
      - [Query Processing](#query-processing)
      - [Query Configuration](#query-configuration)
      - [Scoring Configuration](#scoring-configuration)
      - [Rescorers](#rescorers)
      - [Facet Configuration](#facet-configuration)
      - [Sort Configuration](#sort-configuration)
      - [Misc Configuration](#misc-configuration)
  - [Suggest Service](#suggest-service)



# Configuration

Configuration is essential at OCSS, because it controls the translation of the simple OCS-API to the complex Elasticsearch API.

Per default the standard Spring mechanics are used to provide the configuration for each individual index. That standard is quiet powerful, because you can enable [Spring Cloud Config](https://spring.io/projects/spring-cloud-config) that comes with a lot of different features and supported backends.

If that's not suitable for your use case (because for example you want to integrate OCSS configuration into your own back end), it is also possible to customize the configuration retrieval by adding implementations for [IndexerConfigurationProvider](javadoc.html#apidocs/de/cxp/ocs/spi/indexer/IndexerConfigurationProvider.html) and [SearchConfigurationProvider](javadoc.html#apidocs/de/cxp/ocs/config/SearchConfiguration.html). They are requested whenever the according configuration needs to be loaded.
At the [Java-docs](javadoc.html#apidoc/index.html) you will find all information about the exiting configuration possibilities. All those settings must be provided by the according implementation.

For this documentation the Spring yaml configuration is used, to explain the different settings.

[back to top](#)

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

[back to top](#)

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

[back to top](#)

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

[back to top](#)

---

#### Data Processor Configuration

With the `data-processor-configuration` you can list the data-processors that should be used to transform data.
These can be standard processors shipped with OCSS or custom data-processors.

For data-processors that expect some configuration, it can be specified as key-value map below a key with the processor's classname.
Check the [java-doc of the data-processors](javadoc.html#apidocs/de/cxp/ocs/preprocessor/impl/package-summary.html) about the configuration details.

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

[back to top](#)

---

#### Index Settings

These settings are applied to the according Elasticsearch index after the indexation process. 
They configure how the index data should be scaled (replicated) and how fast data updates should be visible.
[Check the Elasticsearch docs](https://www.elastic.co/guide/en/elasticsearch/reference/7.15/index-modules.html#dynamic-index-settings) about the details.

```yaml
    index-settings:
      replica-count: 2
      refresh-interval: 10s
```

[back to top](#)

---

#### Field Configuration

It's required for the indexer to know which data fields should be indexed in which way. [Learn more about it at the Indexer docs](indexer_service.html).

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

[back to top](#)


## Search Service

To understand the search service configuration (and not duplicate that information), it is recommended to read the [Configuration Paragraph of the Search Service](search_service.html#configuration) first.

Here only the configuration "language" is documented, not all details of the resulting behaviour.

As an example we use the Spring yaml configuration style to describe the settings. There all settings are prefixed with `ocs`. See the [preset configuration](https://github.com/CommerceExperts/open-commerce-search/blob/master/search-service/src/main/resources/application-preset.yml) for an full example.

### Connection and Plugin Configuration

These settings are identical to the one for the [Indexer service](#connection-configuration).


### Default and Specific Tenant Configuration

It is possible to have a default configuration, that is used for all tenants where no specific configuration exists. 

Additionally the tenant's search configuration has the feature to reference parts of the default configuration! This way you can reuse certain configuration blocks.

```yaml
ocs:
  default-tenant-config:
    plugin-configuration:
      ...
    query-processing:
      ...
    query-configuration:
      ...
    scoring-configuration:
      ...
    rescorers:
      ...
    facet-configuration:
      ...
    sort-configuration:
      ...
  tenant-config:
    my-tenant:
      # Similar to the structure of 'default-tenant-config'
      # Additionaly the following options are possible:
      # These boolean properties can be used to use the according
      # default configuration instead defining the same config again
      use-default-query-config: [true|false]
      use-default-scoring-config: [true|false]
      use-default-facet-config: [true|false]
      use-default-sort-config: [true|false]
      # There can be different tenant configuration for the same index
      index-name: "my-index"
      ...
```

[back to top](#)

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

[back to top](#)

---

#### Query Processing

At this section the details are configured, about how a user-query is processed before the Elasticsearch query is built.

- `user-query-preprocessors`: A list of classes that may replace the 
- `user-query-analyzer`: Specify the class that should do the analyzing part. It must be an implementation of the `de.cxp.ocs.spi.search.UserQueryAnalyzer` interface. 
  Custom Implementations and `QuerqyQueryExpander` must be configured via the `plugin-configuration` section.

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

[back to top](#)

---


#### Query Configuration

This configuration part is an ordered map of one or more named query configuration objects. They configure the [Query Relaxation logic](search_service.html#query-relaxation)

Each query configuration consists of a `name`, a `strategy`, a `condition`, the `weighted-fields` that are searched, and some strategy specific `settings`:

```yaml
    query-configuration:
      <name>:
        strategy: "<strategy-name>"
        condition: 
          matchingRegex: "<regex>"
          maxTermCount: <int>
        weightedFields:
          "<field-name>": <float>
          ...
        settings:
          ...
```

[back to top](#)

---

- `strategy`: Per default the following strategies are provided: "DefaultQuery", "ConfigurableQuery", "PredictionQuery", and "NgramQuery".
  Details about their behavior and the supported settings can be found at the java-docs which are linked at the [Query Relaxation docs](search_service.html#query-relaxation)
- `condition`: Defines under which conditions that query strategy is used. Should contain at least one of the following properties:
  - `minTermCount`: `<int>` minimum amount of terms inside the analyzed query.
  - `maxTermCount`: `<int>` maximum amount of terms inside the analyzed query.
  - `matchingRegex`: `<regular-expression-string>` Regular Expression that has to match on the whole query
- `weightedFields`: A map of field names and their weight for that query. The field names must not be prefixed with "searchData", but they *can* be suffixed with one of the analyzed subfields ".standard", ".shingles", or ".ngram". Also they may contain the wildcard `*` for matching multiple fields (including the analyzed subfields).
- `settings`: A map of strategy specific settings that are documented at the java-docs which are linked at the [Query Relaxation docs](search_service.html#query-relaxation)

[back to top](#)

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

[back to top](#)

---

#### Rescorers

A list of canonical class names of custom implementations of the `de.cxp.ocs.spi.search.RescorerProvider` interface.

These customization allows the usage of the [Query Rescorer API](https://www.elastic.co/guide/en/elasticsearch/reference/7.15/filter-search-results.html#rescore).

```yaml
      rescorers:
        - "my.custom.LearningToRankRescorerProvider"
        - "my.custom.FancyRescorer"
```

[back to top](#)

---


#### Facet Configuration

With the facet configuration you can add additional behaviour about how facets are generated. 
It contains the following properties:
- `max-facets`: (default = 5). With this facet you can limit the amount of facets that should be generated. 

  A low number improves performance (and maybe even usability) because less facets are generated. 
  Each individual facet can set the value `excludeFromFacetLimit: true` to make it being generated all the time, ignoring that limit (useful for standard facets). 
  In general the most common facets for a particular result are generated, but if facets are equally common, it's up to Elasticsearch's logic which facets are returned.

- `default-facet-configuration`: A optional configuration with all setting-options that should be applied to all facets that have no specific configuration.
  
  The single properties for this configuration are the same ones as supported for every specific facet. Only the "source-field" and "label" setting are ignored.

- `facets`: A list of individual facet configs. Without an individual facet config, facets are generated with default behaviour.

```yaml
    facet-configuration:
      max-facets: <int>
      default-facet-configuration:
        type: [term|hierarchical|interval|range|ignore|<custom>]
        order: <int>
        value-order: [COUNT|ALPHANUM_ASC|ALPHANUM_DESC]
        optimal-value-count: <int>
        exclude-from-facet-limit: <boolean>
        show-unselected-options: <boolean>
        multi-select: <boolean>
        prefer-variant-on-filter: <boolean>
        min-facet-coverage: <double>
        min-value-count: <int>
        meta-data:
          "<key>": "<value>"
      facets:
      - source-field: "<field-name>"
        label: "<label>"
        type: [term|hierarchical|interval|range|ignore|<custom>]
        order: <int>
        value-order: [COUNT|ALPHANUM_ASC|ALPHANUM_DESC]
        optimal-value-count: <int>
        exclude-from-facet-limit: <boolean>
        show-unselected-options: <boolean>
        multi-select: <boolean>
        prefer-variant-on-filter: <boolean>
        min-facet-coverage: <double>
        min-value-count: <int>
        meta-data:
          "<key>": "<value>"
      - ...
```

Each individual facet config may contain the following properties:

- `source-field`: (required) Specifies for which facet this configuration applies to. Only one facet config is allowed per field.
- `label`: (default = source-field) Defines the 'label' for that facet. Will be part of the returned `meta-data`
- `type`: Per default the type depends on the field type. This is "term" for string fields, "interval" for number fields, and "hierarchical" for category fields. 
  It can also be set to "ignore" to avoid facet creation, even if that facet is indexed.
  For number fields it can also be set to "range" to get a facet with a single entry that contains the global min and max value of that facet.
- `order`: (default = 1000) This order value will be used to sort the facets according to it (low order values are put on a prior position).
- `value-order`: Set the order of the facet values. Defaults to COUNT which means, the value with the highest result coverage will be listed first. Other possible values are 'ALPHANUM_ASC' or 'ALPHANUM_DESC'. This setting is only used for term-facets and category-facets. Category facet will be sorted recursively.
  If the order of two facets is the same, the one that's filtered will be preferd. If both have the same filter-status, the one with the higher result-coverage will be prefered.
- `optimal-value-count`: (default = 5) Only used for "interval" facets to specify how many interval-filter-options should be generated at the maximum (if there enough results).
- `exclude-from-facet-limit`: (default = false) See `max-facets` description above
- `show-unselected-options`: (default = false) If set to "true" all possible facet values will be returned, even if one of them is used as filter. Choosing another filter-option will then toggle the selected filter.
- `multi-select`: (default = false) If set to "true" the behaviour is similar to `show-unselected-options` and additionally choosing another filter-option will filter the result for both of them inclusively (e.g. "blue" or "red").
- `prefer-variant-on-filter`: (default = false) Set to true, if variant documents should be preferred in the result in case a filter of that facet/field is used. This can only be used for facets/fields, that exist on variant level, otherwise it is ignored. If several facets have this flag activated, one of them must be filtered to prefer a variant. E.g. if you have different variants per "color" and "material", and you set this flag for both facets, variants will be shown if there is either a color or a material filter. This setting is ignored if the "variant-picking-strategy" is set to "pickAlways".
- `min-facet-coverage`: (default: 0.1) Must be a floating value between 0 and 1. It defines the share of hits in the result that a facet with its elements should relate to, in order to show that facet. For example the color facet with a min-facet-coverage of 0.2 will only be shown, if 20% of the hits have a color attribute.
- `min-value-count`: (default: 2) An absolute number greater than 0. It defines the minimum amount of filter-elements that facet must have in order to be displayed. For example a facet with 3 filter-elements won't be shown if its `min-value-count` is set to 5. This value is reduced if the whole search-index contains less values for that particular facet field. For example if the "sale" field only contains the value "true", then the `min-value-count` is always set to 1, no matter what is set in the configuration.
- `meta-data`: (default = null) Can be used to add arbitrary data to a facet. The value is a simple string map. This is useful to add configuration values that can be considered at the implementation side. Some internal data is also exposed at that meta-data map (e.g. label and count)


[back to top](#)

---

#### Sort Configuration

This section allows the control of the sorting options inside the response and also their sorting behavior. If this configuration part is missing, the sorting options will be generated from all fields that are indexed for sorting with the label being `fieldName.order`.

Each sorting option configuration supports the following properties:

- `label`: The display label
- `field`: (required) The field that should be used for this sorting option
- `order`: (required: "ASC" or "DESC") specifies the sort order for this sorting option
- `missing`: (default = 0) Defines how documents without data at that field should be treated in that sorting

Example:
```yaml
      sort-configuration:
        - label: "Cheapest first"
          field: price
          order: ASC
          # consider documents without price to be sorted to the end
          missing: 1000
```

[back to top](#)

---

#### Misc Configuration

Some more stand-alone setting options on tenant level:

- `variant-picking-strategy`: Set when variants should be the result hit instead of their main product.
  One of "pickAlways", "pickIfDrilledDown", "pickIfBestScored"(default), "pickIfSingleHit" with the following behavior:
  - "pickAlways": pick first variant if available.
  - "pickIfDrilledDown": Pick best variant if at a single hit some variants were filtered.
  - "pickIfBestScored": Pick first variant, if it has a better score than the second one or if it's the only one left.
  - "pickIfSingleHit": Picks a variant only if there are no other variants matching.

[back to top](#)

---

## Suggest Service

Similar to the other ConfigurationProvider there is also a SuggestConfigProvider interface that can be implemented and added to the suggest-service classpath.
It allows different suggest configurations per index.
A default configuration can also be provided by Java system properties (see below).

Details about the suggest service (host, port, etc.) are configured trough Java's system properties. 
Optionaly you can put a file named `suggest.properties` into classpath, and the Suggest Service will load them into the system properties.

For missing system properties the Suggest Service tries to lookup an environment variable where each dot `.` is replaced by underscore `_` and all letters are uppercase.
(Example: If the property `suggest.index.folder` is undefined, it will lookup the `SUGGEST_INDEX_FOLDER` environment variable)

Due to simplicity and having a proper blueprint, the properties are presented as a properties file including all explanation as comments and all default values already set.

```properties
# server listening settings
suggest.server.port=8080
suggest.server.adress=0.0.0.0

# how often (in seconds) are the data providers asked if the have new data
suggest.update.rate=60

# Normally the data for an index is loaded when the first request comes in.
# With this setting, you can name the indexes that should be loaded directly at the start.
# Values should be comma-separated - index names MUST NOT contain commas.
# Example: suggest.preload.indexes=myindex1,myindex2
# 
#suggest.preload.indexes=

# Specify where lucene puts the indexes. If not specified, the temporary 
# directory will be used.
#
#suggest.index.folder=

# If several suggest-data-providers are used, they are indexed into separate indexes by default. This option
# activates a merging logic, so that all provided data is merged into one index.
#
# This could reduce load and improve performance since a single Lucene suggester is asked for results.
# However in such a case the weights should be in a similar range to avoid a proper ranking.
#
# Default: false
#
#suggest.data.source.merger=false

# If this property is set, it will be used to extract the payload value with
# this key and group the suggestions accordingly.
# It's recommended to specify 'suggest.group.share.conf' or
# 'suggest.group.cutoff.conf' as well, otherwise the default limiter will
# be used after grouping.
#
#suggest.group.key=

# Depends on a configured `suggest.group.key` property
# The property changes the way, how the result list is truncated (limited).
# Expects the property in the format 'group1=0.x,group2=0.x' to be used as 
# group-share configuration for the 'ConfigurableShareLimiter'
# See the [java doc](javadoc.html#apidocs/de/cxp/ocs/smartsuggest/limiter/ConfigurableShareLimiter.html)
# for more details.
# Basically these values configure, which group of suggestions should get which
# share in the result (e.g. keywords=0.5 (50%), brand=0.3 (30%), category=0.2 (20%)).
#
# This ConfigurableShareLimiter also reads env variables, however they can
# also be configured here directly, but all in upper case, like that:
# SUGGEST_GROUP_SHARE_BRAND=
#
#suggest.group.share.conf=

# Depends on a configured `suggest.group.key` property
# Expects the property to be specified in the format 'group1=N,group2=M'
# with the group names that exist in your suggestion data and integer values.
# The values are considered as absolute limites.
#
#suggest.group.cutoff.conf=

# If grouping and limiting is configured by a key that comes from a single or merged data-provider, then this value
# can be used to increase the internal amount of fetched suggestions.
# This is usable to increase the likeliness to get the desired group counts.
#
# Default: 1
#
#suggest.group.prefetch.limit.factor=1

# If this property is set, the returned values will be deduplicated. As a value
# a comma separated list of the group-values can be specified. It's used as
# a priority order: suggestions of the groups defined first will be
# preferred over suggestions from other groups. Example: a value
# "brand,keyword" will be used to remove a keyword suggestions if there is
# a similar brand suggestions. Comparison is done on normalized values
# (lowercase+trim). Defining the property without a value will enable
# deduplication, but will do that without any priorization.
#
#suggest.group.deduplication.order=

# Optional path prefix for the '/health' and '/metrics' endpoint.
#suggest.mgmt.path.prefix=

# If a suggest index is not requested for that time, it will be unloaded.
# A new request to that index will return an empty list, but restart the loading
# of that index.
suggester.max.idle.minutes=30

```

[back to top](#)
