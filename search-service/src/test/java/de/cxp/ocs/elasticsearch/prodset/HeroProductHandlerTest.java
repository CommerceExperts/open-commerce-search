package de.cxp.ocs.elasticsearch.prodset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.util.Arrays;
import java.util.Collections;
import java.util.Random;
import java.util.Set;

import org.junit.jupiter.api.Test;

import de.cxp.ocs.SearchContext;
import de.cxp.ocs.config.SearchConfiguration.ProductSetType;
import de.cxp.ocs.elasticsearch.Searcher;
import de.cxp.ocs.model.params.GenericProductSet;
import de.cxp.ocs.model.params.ProductSet;
import de.cxp.ocs.model.params.StaticProductSet;

public class HeroProductHandlerTest {

	@Test
	public void testNoGenericResolverProvided() {
		HeroProductHandler underTest = new HeroProductHandler(Collections.emptyMap());
		
		Searcher searcherMock = mock(Searcher.class);
		SearchContext context = new SearchContext(null, null, null, null);
		
		GenericProductSet genericProductSet = new GenericProductSet("x", 3, Collections.singletonMap("inject", "something"));
		StaticProductSet[] resolved = underTest.resolve(new ProductSet[] { genericProductSet }, searcherMock, context);

		assertTrue(resolved.length > 0);
		assertTrue(resolved[0].ids.length == 0, () -> Arrays.toString(resolved[0].ids));
		assertEquals(0, resolved[0].getSize());
	}

	@Test
	public void testInjectedGenericResolverUsed() {
		final GenericProductSet genericProductSet = new GenericProductSet("x", new Random().nextInt(10) + 1, Collections.singletonMap("injectionQuery", "blub"));
		final StaticProductSet expectedResult = new StaticProductSet("generic", new String[] { "1251" }, "injected-bla");

		ProductSetResolver resolverStub = new ProductSetResolver() {

			@Override
			public StaticProductSet resolve(ProductSet set, Set<String> excludedIds, Searcher searcher, SearchContext searchContext) {
				assertTrue(set == genericProductSet);
				return expectedResult;
			}
		};

		HeroProductHandler underTest = new HeroProductHandler(Collections.singletonMap(ProductSetType.Generic, resolverStub));

		Searcher searcherMock = mock(Searcher.class);
		SearchContext context = new SearchContext(null, null, null, null);
		StaticProductSet[] resolved = underTest.resolve(new ProductSet[] { genericProductSet }, searcherMock, context);

		assertTrue(resolved[0] == expectedResult);
	}

}
