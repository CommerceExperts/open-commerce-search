package de.cxp.ocs.elasticsearch.query;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.elasticsearch.core.Map;
import org.elasticsearch.index.query.QueryStringQueryBuilder;
import org.junit.jupiter.api.Test;

import de.cxp.ocs.elasticsearch.model.query.ExtendedQuery;
import de.cxp.ocs.elasticsearch.query.analyzer.QuerqyQueryExpander;
import de.cxp.ocs.elasticsearch.query.analyzer.QuerqyQueryExpanderBuilder;

public class StandardQueryFactoryTest {

	QuerqyQueryExpanderBuilder qqBuilder = new QuerqyQueryExpanderBuilder();

	@Test
	public void testMultiTermSynonyms() {
		StandardQueryFactory underTest = new StandardQueryFactory(Map.of(), Map.of());

		QuerqyQueryExpander analyzer = qqBuilder.loadWithRules("parkside performance =>", "  SYNONYM(1.6): baumarkt", "  SYNONYM(1.3): garten & balkon");
		ExtendedQuery analyzedQuery = analyzer.analyze("parkside performance");
		System.out.println(analyzedQuery);

		QueryStringQueryBuilder esQuery = underTest.create(analyzedQuery);
		assertEquals("((((parkside OR baumarkt^1.6) (performance OR baumarkt^1.6)) OR \"garten & balkon\"^1.3)) OR \"parkside performance\"^1.5", esQuery.queryString());
	}

}
