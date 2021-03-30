package de.cxp.ocs.spi.search;

import java.util.Map;
import java.util.Optional;

import org.elasticsearch.search.rescore.RescorerBuilder;

public interface RescorerProvider extends ConfigurableExtension {

	Optional<RescorerBuilder<?>> get(String userQuery, Map<String, String> customParams);

}
