package de.cxp.ocs.elasticsearch.query;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;

import java.util.Optional;

import org.elasticsearch.core.Map;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.BoostingQueryBuilder;
import org.elasticsearch.index.query.QueryStringQueryBuilder;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import de.cxp.ocs.config.Field;
import de.cxp.ocs.config.FieldConfigAccess;
import de.cxp.ocs.config.FieldUsage;
import de.cxp.ocs.elasticsearch.model.query.ExtendedQuery;
import de.cxp.ocs.elasticsearch.query.analyzer.QuerqyQueryExpander;
import de.cxp.ocs.elasticsearch.query.analyzer.QuerqyQueryExpanderBuilder;

public class StandardQueryFactoryTest {

	QuerqyQueryExpanderBuilder qqBuilder = new QuerqyQueryExpanderBuilder();

	@Test
	public void testMultiTermSynonyms() {
		StandardQueryFactory underTest = new StandardQueryFactory(Map.of(), Map.of(), null);

		QuerqyQueryExpander analyzer = qqBuilder.loadWithRules("super performance =>", "  SYNONYM(1.6): akku", "  SYNONYM(1.3): speicher & balkon");
		ExtendedQuery analyzedQuery = analyzer.analyze("super performance");
		System.out.println(analyzedQuery);

		QueryStringQueryBuilder esQuery = (QueryStringQueryBuilder) underTest.create(analyzedQuery).getQueryBuilder();
		assertEquals("((((super OR akku^1.6) (performance OR akku^1.6)) OR \"speicher & balkon\"^1.3)) OR \"super performance\"^1.5", esQuery.queryString());
	}

	@Test
	public void testBoostedQuery() {
		FieldConfigAccess fieldConfigMock = Mockito.mock(FieldConfigAccess.class);
		Mockito.when(fieldConfigMock.getMatchingField(eq("brand"), eq(FieldUsage.SEARCH))).thenReturn(Optional.of(new Field("brand")));
		StandardQueryFactory underTest = new StandardQueryFactory(Map.of(), Map.of(), fieldConfigMock);

		QuerqyQueryExpander analyzer = qqBuilder.loadWithRules("notebook =>", "  UP(2): * brand:lenovo", "  DOWN(0.3): accessory");
		ExtendedQuery analyzedQuery = analyzer.analyze("notebook");
		System.out.println(analyzedQuery);

		BoostingQueryBuilder esQuery = (BoostingQueryBuilder) underTest.create(analyzedQuery).getQueryBuilder();
		assertEquals("++brand:lenovo(2.0)", ((BoolQueryBuilder) esQuery.positiveQuery()).should().get(0).queryName(), esQuery::toString);
		assertEquals("--accessory", esQuery.negativeQuery().queryName(), esQuery::toString);
	}

}
