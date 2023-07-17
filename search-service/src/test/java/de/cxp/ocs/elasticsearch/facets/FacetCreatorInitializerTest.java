package de.cxp.ocs.elasticsearch.facets;

import static de.cxp.ocs.elasticsearch.facets.FacetCreatorClassifier.hierarchicalFacet;
import static de.cxp.ocs.elasticsearch.facets.FacetCreatorClassifier.masterIntervalFacet;
import static de.cxp.ocs.elasticsearch.facets.FacetCreatorClassifier.masterRangeFacet;
import static de.cxp.ocs.elasticsearch.facets.FacetCreatorClassifier.masterTermFacet;
import static de.cxp.ocs.elasticsearch.facets.FacetCreatorClassifier.variantIntervalFacet;
import static de.cxp.ocs.elasticsearch.facets.FacetCreatorClassifier.variantRangeFacet;
import static de.cxp.ocs.elasticsearch.facets.FacetCreatorClassifier.variantTermFacet;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collection;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;

import de.cxp.ocs.config.FacetConfiguration.FacetConfig;
import de.cxp.ocs.config.FacetType;
import de.cxp.ocs.config.Field;
import de.cxp.ocs.config.FieldType;
import de.cxp.ocs.spi.search.CustomFacetCreator;

public class FacetCreatorInitializerTest {

	private static final Map<String, Supplier<? extends CustomFacetCreator>> noCustomFacetCreators = Collections.emptyMap();

	private static final Function<String, FacetConfig>	defaultTermFacetProvider	= name -> new FacetConfig(name, name).setType(FacetType.TERM.toString());
	private static final Function<String, FacetConfig>	defaultNumberFacetProvider	= name -> new FacetConfig(name, name).setType(FacetType.INTERVAL.toString());

	@Test
	public void testNoConfigInit() {
		FacetCreatorInitializer underTest = new FacetCreatorInitializer(noCustomFacetCreators, defaultTermFacetProvider, defaultNumberFacetProvider, Locale.ROOT, 12);
		Map<FacetCreatorClassifier, FacetCreator> facetCreators = underTest.init();

		assertInstanceOfAndGet(facetCreators.get(hierarchicalFacet), CategoryFacetCreator.class);
		assertInstanceOfAndGet(facetCreators.get(masterTermFacet), TermFacetCreator.class);
		assertInstanceOfAndGet(facetCreators.get(variantTermFacet), VariantFacetCreator.class);

		Collection<FacetCreator> variantTermFacetCreator = ((VariantFacetCreator) facetCreators.get(variantTermFacet)).getInnerCreators();
		assertEquals(1, variantTermFacetCreator.size());
		{
			var innerFC = variantTermFacetCreator.iterator().next();
			assertInstanceOfAndGet(innerFC, TermFacetCreator.class);
		}

		assertInstanceOfAndGet(facetCreators.get(masterIntervalFacet), IntervalFacetCreator.class);
		assertInstanceOfAndGet(facetCreators.get(variantIntervalFacet), VariantFacetCreator.class);

		Collection<FacetCreator> variantIntervalFacetCreator = ((VariantFacetCreator) facetCreators.get(variantIntervalFacet)).getInnerCreators();
		assertEquals(1, variantIntervalFacetCreator.size());
		{
			var innerFC = variantIntervalFacetCreator.iterator().next();
			assertInstanceOfAndGet(innerFC, IntervalFacetCreator.class);
		}

		assertNull(facetCreators.get(masterRangeFacet));
		assertNull(facetCreators.get(variantRangeFacet));
		assertEquals(5, facetCreators.size());
	}

	@Test
	public void testCategoryConfig() {
		FacetCreatorInitializer underTest = new FacetCreatorInitializer(noCustomFacetCreators, defaultTermFacetProvider, defaultNumberFacetProvider, Locale.ROOT, 12);
		FacetConfig categoryFacetConfig = new FacetConfig("Category", "c").setType(FacetType.HIERARCHICAL.name()).setMinValueCount(3);
		underTest.addFacet(new Field("c").setType(FieldType.CATEGORY), categoryFacetConfig);

		Map<String, FacetConfig> hierarchicalFacetConfigs = assertInstanceOfAndGet(underTest.init().get(hierarchicalFacet), CategoryFacetCreator.class).getFacetConfigs();
		assertEquals(1, hierarchicalFacetConfigs.size());
		assertEquals(categoryFacetConfig, hierarchicalFacetConfigs.values().iterator().next());
	}

	@Test
	public void testDefaultRangeFacetCreator() {
		Function<String, FacetConfig> defaultNumbRangeFacetProvider = name -> new FacetConfig(name, name).setType(FacetType.RANGE.toString());
		FacetCreatorInitializer underTest = new FacetCreatorInitializer(noCustomFacetCreators, defaultTermFacetProvider, defaultNumbRangeFacetProvider, Locale.ROOT, 12);
		Map<FacetCreatorClassifier, FacetCreator> facetCreators = underTest.init();

		assertInstanceOfAndGet(facetCreators.get(masterRangeFacet), RangeFacetCreator.class);
		Collection<FacetCreator> variantNumFacetCreators = assertInstanceOfAndGet(facetCreators.get(variantRangeFacet), VariantFacetCreator.class).getInnerCreators();
		assertEquals(1, variantNumFacetCreators.size());
		{
			var innerFC = variantNumFacetCreators.iterator().next();
			assertInstanceOfAndGet(innerFC, RangeFacetCreator.class);
		}

		assertNull(facetCreators.get(masterIntervalFacet));
		assertNull(facetCreators.get(variantIntervalFacet));
	}

	private <T> T assertInstanceOfAndGet(Object o, Class<T> clazz) {
		assertNotNull(o);
		assertTrue(clazz.isAssignableFrom(o.getClass()), () -> "expected to be of class " + clazz.getName() + " but was of " + o.getClass().getName());
		return clazz.cast(o);
	}

}
