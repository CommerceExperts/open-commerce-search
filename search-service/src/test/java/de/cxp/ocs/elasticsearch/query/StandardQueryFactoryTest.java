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

		QuerqyQueryExpander analyzer = qqBuilder.loadWithRules("super performance =>", "  SYNONYM(1.6): akku", "  SYNONYM(1.3): speicher & balkon");
		ExtendedQuery analyzedQuery = analyzer.analyze("super performance");
		System.out.println(analyzedQuery);

		QueryStringQueryBuilder esQuery = underTest.create(analyzedQuery);
		assertEquals("((((super OR akku^1.6) (performance OR akku^1.6)) OR \"speicher & balkon\"^1.3)) OR \"super performance\"^1.5", esQuery.queryString());
	}

}
