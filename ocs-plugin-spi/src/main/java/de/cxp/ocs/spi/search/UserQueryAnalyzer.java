package de.cxp.ocs.spi.search;

import de.cxp.ocs.elasticsearch.model.query.ExtendedQuery;

public interface UserQueryAnalyzer extends ConfigurableExtension {

	ExtendedQuery analyze(String userQuery);

}
