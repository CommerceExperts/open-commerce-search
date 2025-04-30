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
Instead each instance fetches and indexes the data on its own using "Data Providers". This data provider is asked if it can deliver data for a requested index and it also has
the modification time of the given data. If there is new data, the suggester library will fetch and index that provided data into the local environment.

Since this architecture is sensitive to potential performance bottle necks at the data providers, that "update" job is done asynchronously in the background of the Suggest Service instances.
However, indexing inside a suggester service in a cluster environment with multiple instances might be not optimal, even more when it needs more time. 
Therefor the suggester library has the ability to archive and restore lucene indexes directly. More about it in the Archive and Restore section below.   

Per default, OCSS comes with a data provider, that fetches certain data fields from the according OCS-Elasticsearch index. 
This can be used to provide suggestions for categories, brands and product titles for example.

For more advanced scenarios the "SuggestDataProvider" interface must be implemented and added to the classpath according to the "[Java's ServiceLoader mechanism](plugin_guide.html#extending-ocs-the-plugin-guide)".

[back to top](#)


## Archive and Restore

To prevent the same data from being indexed across multiple instances, you can utilize the "IndexArchiveProvider". This provider should facilitate the uploading and downloading of files to and from external storage solutions, such as S3 or GC-Storage.

Now you can start your suggest services only with your IndexArchiverProvider implementation, removing or disabling all the SuggestDataProvider implementations. Additionally, you will need an indexer instance that operates with the same IndexArchiverProvider and all SuggestDataProvider instances from which the source data is retrieved. When this indexer detects new data from the data providers, it will index it and push it to the IndexArchiverProvider. The other instances will then recognize the new IndexArchives, pulling and utilizing them directly.

Alternatively that Indexer-Instance can also run in a cronjob like manner, minimizing stress from live traffic during indexing. To achieve this, pass the program argument 'only-update-archives' to the server application, which will execute the updates and then shut down.

If you are using multiple data sources for a single index, you must either set `suggestConfig.useDataSourceMerger = true` to consolidate all data into a single index or extend the `CompoundIndexArchiveProvider` to support arching the data of a compound suggester. The compound suggester is preferable when your suggestion results need to include various types of suggestions while *ensuring* a balanced representation of each type.

[back to top](#)


## Configuration Options

[See configuration docs](configuration.html#suggest-service)


