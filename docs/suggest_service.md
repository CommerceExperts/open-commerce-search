[Home](./) > [Suggest Service](./suggest_service.md)

# Suggest Service

- [API](#api)
- [Implementation Overview](#implementation-overview)
  - [Staged Search](#staged-search)
- [Data Providers](#data-providers)
- [Configuration Options](#configuration-options)


## API

The REST API is quite trivial. You send in a (partial) query and in return you get a list of matching suggestions (e.g. query autocompletions, category or brand links). :)

- Request: `/suggest-api/v1/{indexname}/suggest?userQuery={q}`
- Optional Parameters:
  - `filter={val1}[,{val2}]*` Optional comma-separated list of filter values. Will be used to filter the suggestions by their 'tags'. This only works if suggestions are actually tagged.
  - `limit={num}` Change the max. amount of returned suggestions. Defaults to 10.
- Response: Array of Suggestion items.

  Each Suggestion item contains 3 fields:
  - `phrase`: The keywords that can be used for a full search.
  - `type`: The type of suggestions. Per default this is `keyword` but could be anything depending on the used [Data Providers](#data-providers)
  - `payload`: Arbitrary payload attached to that suggestion that came by the according DataProvider. Defaults to `null`.


[back to top](#)

## Implementation Overview

For this service we omited the usage of Elasticsearch. Instead we use pure lucene to use its full potential. 
Internally several indexes are created, each prepared for different search approaces, all running after the other until the requested limit is reached. We name that "Staged Search".

To understand the different stages, you need to know, that the provided data contains a "primary text" and a "secondary text". 
For the full structure of the provided `SuggestRecord`s, have a look at the [Data Providers](#data-providers) below.

### Staged Search

These stages are used to build the final suggest result:

0. Check if there are 'sharpened terms' provided by any DataProvider. These terms are looked up only for the full matching query.
1. Using an Infix Suggester to search the primary text only
2. Using an Infix Suggester to search the secondary text
3. If the query has 3 or more characters and no matches were done until now, a FuzzySuggester is used to search the primary text that has a max. Edit Distance of 1
4. Same as step 3, but with a max. Edit Distance of 2
5. If no matches were done until now, a Infix Suggester is used to search 2-word shingles created from the secondary text.
6. Similar to step 0 there also could be 'relaxed terms' that are searched at the final stage. These can be used for certain queries to suggest something completely different.

These stages are only searched as long as there are not enough matches. 

With one optional exception the order of the result stays grouped by these stages. (*See the configuration option `doReorderSecondaryMatches` for that one exception*).

The matching stage will be added as payload to each Suggestion item with the key `meta.matchGroupName`.

In case the whole result computation needs more than 100ms, an `INFO` message is logged with details about the performance of each stage. If it needs more than 200ms, the same message is logged on `WARN` level.

[back to top](#)


## Data Providers

The Suggest Service has no "indexation" API because with that there would be a need to orchestrate the indexation and distribute the created indexes in a distributed scenario. 
Instead each instance fetches and indexes the data on its own using "Data Providers". 

Since this architecture moves a potential performance bottle neck to the data providers, that "update" job is done asychronously in the background of the Suggest Service instances.

Per default, OCSS comes with a data provider, that fetches certain data fields from the according OCS-Elasticsearch index. This can be used to provide suggestions for categories, brands and product titles for example.

For more advanced scenarios a data provider must be implemented and added to the classpath using "[Java's ServiceLoader mechanism](plugin_guide.html#extending-ocs-the-plugin-guide)".

[back to top](#)


## Configuration Options

The options of the "SmartSuggest Library" are used by the Suggest Service, configurable trough Java's system properties. 
Optionaly you can put a file `suggest.properties` somewhere at classpath, the Suggest Service will load them into the system properties.

For missing system properties the Suggest Service tries to lookup an environment variable where each dot `.` is replaced by underscore `_` and all letters are uppercase.

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



