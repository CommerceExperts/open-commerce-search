package de.cxp.ocs.spi.search;

import java.util.List;

import de.cxp.ocs.elasticsearch.query.model.QueryStringTerm;

public interface UserQueryAnalyzer extends ConfigurableExtension {

	List<QueryStringTerm> analyze(String userQuery);

}
