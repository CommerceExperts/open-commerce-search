[Home](./) > [Plugin Guide](./plugin_guide.md)

## Table of Contents

- [Extending OCS - the Plugin Guide](#extending-ocs-the-plugin-guide)
  - [Indexer SPI](#indexer-spi)
    - [de.cxp.ocs.spi.indexer.IndexerConfigurationProvider](#decxpocsspiindexerindexerconfigurationprovider)
    - [de.cxp.ocs.spi.indexer.DocumentPreProcessor](#decxpocsspiindexerdocumentpreprocessor)
    - [de.cxp.ocs.spi.indexer.DocumentPostProcessor](#decxpocsspiindexerdocumentpostprocessor)
  - [Search-Service SPI](#search-service-spi)
    - [de.cxp.ocs.spi.search.SearchConfigurationProvider](#decxpocsspisearchsearchconfigurationprovider)
    - [de.cxp.ocs.spi.search.ESQueryFactory](#decxpocsspisearchesqueryfactory)
    - [de.cxp.ocs.spi.search.RescorerProvider](#decxpocsspisearchrescorerprovider)
    - [de.cxp.ocs.spi.search.UserQueryAnalyzer](#decxpocsspisearchuserqueryanalyzer)
    - [de.cxp.ocs.spi.search.UserQueryPreprocessor](#decxpocsspisearchuserquerypreprocessor)

# Extending OCS - the Plugin Guide

As plugin machanism the "Service Loader" mechanism of Java is used. It is available since JDK6 and requires nothing but according interface implementations on the classpath.

For example to supply a custom implementation of the indexer's `de.cxp.ocs.spi.indexer.DocumentPreProcessor`, you have to provide a file with the full qualified name at `META-INF/services/` (on classpath of the according java process - in this case at the Indexer) that contains the full qualified name(s) of the provided implementations (one per line in case there are several). More details can be found in the web or at the [official documentation](https://docs.oracle.com/javase/tutorial/ext/basics/spi.html).

It makes totally sense to put all your customizations for the different OCSS services into a single component / JAR and put them into each service class path. The service are built to load the customizations gracefuly and only actively use them if configured.

The Service-Provider-Interfaces of Indexer and Search-Service are defined at the [ocs-plugin-spi](https://github.com/CommerceExperts/open-commerce-search/tree/master/ocs-plugin-spi) component. For Suggest-Service the data provider interface is part of the main library.

Add the according dependencies to your "custom plugin" project:

```xml
  <dependencies>
  
    <!-- for Indexer / Search Service customization -->
    <dependency>
      <groupId>de.cxp.ocs</groupId>
      <artifactId>ocs-plugin-spi</artifactId>
      <scope>provided</scope>
      <!-- please lookup current version -->
      <version>0.9.0</version>
    </dependency>

    <!-- for Suggest Customization -->
    <dependency>
      <groupId>de.cxp.ocs</groupId>
      <artifactId>smartsuggest-lib</artifactId>
      <scope>provided</scope>
      <!-- please lookup current version -->
      <version>0.6.0</version>
    </dependency>
    
  </dependencies>
  
  <!-- since we are not yet on maven central, you also
       need to define the source for these dependencies -->
  <repositories>
    <repository>
      <id>cxp-public-releases</id>
      <url>https://nexus.commerce-experts.com/content/repositories/searchhub-external/</url>
    </repository>
  </repositories>
```

[back to top](#)

## Indexer SPI

### de.cxp.ocs.spi.indexer.IndexerConfigurationProvider

By default the Indexer fetches the configuration from the Spring-managed application properties. To use your own configuration backend you can implement this interface to build the required indexer configuration.

[See details at the Java-docs](javadoc.html#apidocs/de/cxp/ocs/spi/indexer/IndexerConfigurationProvider.html)

### de.cxp.ocs.spi.indexer.DocumentPreProcessor

In case you don't have influence on the incoming data or want to use some central place to modify the data before it's indexed, you can implement this interface. It get's the incoming Document before it is transformed into the `IndexableItem`. Using the boolean return value it's also possible to deny the indexation of certain documents.

These DocumentPreProcessors need to be activated at the Indexer's configuration in order to use them for certain indexes. At the configuration it's also possible to add some simple configuration for the DocumentPreProcessor.

[See details at the Java-docs](javadoc.html#apidocs/de/cxp/ocs/spi/indexer/DocumentPreProcessor.html)

### de.cxp.ocs.spi.indexer.DocumentPostProcessor

It works very much like the DocumentPreProcessor with the only difference, that there you have the possibility to modify the prepared IndexableItem before it's pushed into Elasticsearch. The IndexableItem has the data split according to it's usage: Search, Sort, Facet, Result, Score. So with this processor it is possible to modify data fields only in context of it's usage, for example cleaning the searchable content or normalizing scorable values.

[See details at the Java-docs](javadoc.html#apidocs/de/cxp/ocs/spi/indexer/DocumentPostProcessor.html)

[back to top](#)

## Search-Service SPI

### de.cxp.ocs.spi.search.SearchConfigurationProvider

Makes it possible to provide the search configuration from a different source. By default the Spring configuration mechanims are used.

[See details at the Java-docs](javadoc.html#apidocs/de/cxp/ocs/spi/search/SearchConfigurationProvider.html)

### de.cxp.ocs.spi.search.ESQueryFactory

A reusable query factory that receives the analyzed user query to build Elasticsearch queries (one for main level and one for the variant level).
The implementation must be thread-safe, since it's reused for every single incoming user query. The initialize method is only called once (per tenant / search context).

[See details at the Java-docs](javadoc.html#apidocs/de/cxp/ocs/spi/search/ESQueryFactory.html)

### de.cxp.ocs.spi.search.RescorerProvider

A provider to inject a [rescorer](https://www.elastic.co/guide/en/elasticsearch/reference/master/filter-search-results.html#rescore) into the Elasticsearch query.
Several RescorerProvider can be used.

[See details at the Java-docs](javadoc.html#apidocs/de/cxp/ocs/spi/search/RescorerProvider.html)

### de.cxp.ocs.spi.search.UserQueryAnalyzer

Makes it possible to change the way how the user query is splitted and transformed into single terms. Per default the `de.cxp.ocs.elasticsearch.query.analyzer.WhitespaceAnalyzer` is used. However the Search-Service contains the powerful `de.cxp.ocs.elasticsearch.query.analyzer.QuerqyQueryExpander` that can be used to inject synonyms or filters based on the user keywords. This is the recommended way to do query-analyzation, but it needs some configuration.

A custom UserQueryAnalyzer could extend one of those existing analyzers or you can provide your own advanced version.

[See details at the Java-docs](javadoc.html#apidocs/de/cxp/ocs/spi/search/UserQueryAnalyzer.html)

### de.cxp.ocs.spi.search.UserQueryPreprocessor

Adds the possiblity to modify the user query before it's passed into the UserQueryAnalyzer. Could be used for spellchecking or query filtering.
Severl implementations can be used per tenant.

[See details at the Java-docs](javadoc.html#apidocs/de/cxp/ocs/spi/search/UserQueryPreprocessor.html)

[back to top](#)

## Suggest-Service SPI

### de.cxp.ocs.smartsuggest.spi.SuggestDataProvider

With an implementation of that interface you provide other data sources to the suggest service.

[See details at the Java-docs](javadoc.html#apidocs/de/cxp/ocs/smartsuggest/spi/SuggestDataProvider.html)

[back to top](#)
