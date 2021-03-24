package de.cxp.ocs.spi.search;

import java.util.Map;

import org.elasticsearch.search.rescore.RescorerBuilder;

public interface RescorerProvider {

	RescorerBuilder<?> get(String userQuery, Map<String, String> customParams);

}
