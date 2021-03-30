package de.cxp.ocs.elasticsearch.query;

import de.cxp.ocs.spi.search.UserQueryPreprocessor;
import de.cxp.ocs.util.StringUtils;

public class AsciifyUserQueryPreprocessor implements UserQueryPreprocessor {

	@Override
	public String preProcess(String userQuery) {
		return StringUtils.asciify(userQuery);
	}

}
