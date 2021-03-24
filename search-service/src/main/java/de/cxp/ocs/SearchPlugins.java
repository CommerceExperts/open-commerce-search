package de.cxp.ocs;

import java.util.List;
import java.util.Optional;

import de.cxp.ocs.spi.search.ESQueryFactory;
import de.cxp.ocs.spi.search.SearchConfigurationProvider;
import de.cxp.ocs.spi.search.UserQueryAnalyzer;
import de.cxp.ocs.spi.search.UserQueryPreprocessor;
import lombok.Data;

@Data
public class SearchPlugins {

	private SearchConfigurationProvider configurationProvider;

	private List<ESQueryFactory> esQueryFactories;

	private Optional<UserQueryAnalyzer> userQueryAnalyzers;

	private List<UserQueryPreprocessor> userQueryPreprocessors;

}
