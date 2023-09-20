package de.cxp.ocs;

import java.util.List;

import de.cxp.ocs.config.FieldConfigIndex;
import de.cxp.ocs.config.SearchConfiguration;
import de.cxp.ocs.elasticsearch.prodset.HeroProductHandler;
import de.cxp.ocs.spi.search.UserQueryPreprocessor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Holder for several tenant specific objects.
 */
@RequiredArgsConstructor
@Getter
public class SearchContext {

	public final FieldConfigIndex fieldConfigIndex;

	public final SearchConfiguration config;
	
	public final List<UserQueryPreprocessor> userQueryPreprocessors;

	public final HeroProductHandler heroProductHandler;

}
