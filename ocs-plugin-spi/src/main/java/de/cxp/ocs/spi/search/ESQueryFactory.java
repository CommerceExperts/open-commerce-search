package de.cxp.ocs.spi.search;

import java.util.Map;

import org.elasticsearch.index.query.QueryBuilder;

import de.cxp.ocs.config.FieldConfigAccess;
import de.cxp.ocs.config.QueryBuildingSetting;
import de.cxp.ocs.elasticsearch.model.query.ExtendedQuery;
import de.cxp.ocs.elasticsearch.query.MasterVariantQuery;

/**
 * <p>
 * A reusable query factory that receives the analyzed user query to build
 * Elasticsearch queries (one for Master level and one for the variant level).
 * </p>
 * <p>
 * The implementation must have a no-args-constructor and must be thread-safe.
 */
public interface ESQueryFactory {

	void initialize(String name, Map<QueryBuildingSetting, String> settings, Map<String, Float> fieldWeights, FieldConfigAccess fieldConfig);

	MasterVariantQuery<QueryBuilder> createQuery(ExtendedQuery parsedQuery);

	boolean allowParallelSpellcheckExecution();

	String getName();
}
