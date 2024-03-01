package de.cxp.ocs.elasticsearch.query.builder;

import de.cxp.ocs.spi.search.ESQueryFactory;

public interface FallbackConsumer {

	void setFallbackQueryBuilder(ESQueryFactory fallbackQuery);

}
