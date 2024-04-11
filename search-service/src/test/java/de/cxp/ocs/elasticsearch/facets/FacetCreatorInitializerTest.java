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

import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;

import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.MultiBucketsAggregation;
import org.elasticsearch.search.aggregations.bucket.terms.Terms.Bucket;
import org.junit.jupiter.api.Test;

import de.cxp.ocs.config.*;
import de.cxp.ocs.config.FacetConfiguration.FacetConfig;
import de.cxp.ocs.elasticsearch.model.filter.InternalResultFilter;
import de.cxp.ocs.model.result.Facet;
import de.cxp.ocs.spi.search.CustomFacetCreator;
import de.cxp.ocs.util.LinkBuilder;

public class FacetCreatorInitializerTest {

	private static final Map<String, Supplier<? extends CustomFacetCreator>> noCustomFacetCreators = Collections.emptyMap();

	private static final Function<String, FacetConfig>	defaultTermFacetProvider	= name -> new FacetConfig(name, name).setType(FacetType.TERM.toString());
	private static final Function<String, FacetConfig>	defaultNumberFacetProvider	= name -> new FacetConfig(name, name).setType(FacetType.INTERVAL.toString());

	@Test
	public void testNoConfigInit() {
		FacetCreatorInitializer underTest = new FacetCreatorInitializer(noCustomFacetCreators, defaultTermFacetProvider, defaultNumberFacetProvider, Locale.ROOT, 12);
		Map<FacetCreatorClassifier, FacetCreator> facetCreators = underTest.init();
		assertDefaultFacetCreators(facetCreators);
		assertEquals(5, facetCreators.size()); // assert there are no more
	}

	private void assertDefaultFacetCreators(Map<FacetCreatorClassifier, FacetCreator> facetCreators) {
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

	@Test
	public void testCustomConfig() {
		Map<String, Supplier<? extends CustomFacetCreator>> customFacetCreators = new HashMap<>();
		final String CUSTOM_FACET_TYPE = "SignificantTerms";
		final String CUSTOM_AGG_NAME = "_customTerms";

		customFacetCreators.put(CUSTOM_FACET_TYPE, () -> new CustomFacetCreator() {

			@Override
			public Optional<Facet> mergeFacets(Facet first, Facet second) {
				return Optional.of(first);
			}

			@Override
			public String getFacetType() {
				return CUSTOM_FACET_TYPE;
			}

			@Override
			public FieldType getAcceptibleFieldType() {
				return FieldType.STRING;
			}

			@Override
			public Optional<Facet> createFacet(Bucket facetNameBucket, FacetConfig facetConfig, InternalResultFilter facetFilter, LinkBuilder linkBuilder, Function<MultiBucketsAggregation.Bucket, Long> nestedValueBucketDocCountCorrector) {
				Facet facet = new Facet(facetConfig.getSourceField());
				facet.addEntry("a", 12, linkBuilder.withFilterAsLink(facetConfig.getSourceField(), false, "a"));
				facet.addEntry("b", 5, linkBuilder.withFilterAsLink(facetConfig.getSourceField(), false, "b"));
				return Optional.of(facet);
			}

			@Override
			public AggregationBuilder buildAggregation(String fullFieldName) {
				assertNotNull(fullFieldName);
				assertEquals(FieldConstants.TERM_FACET_DATA + ".value", fullFieldName);
				return AggregationBuilders.significantTerms(CUSTOM_AGG_NAME).field(fullFieldName);
			}
		});

		// :: setup
		FacetCreatorInitializer underTest = new FacetCreatorInitializer(customFacetCreators, defaultTermFacetProvider, defaultNumberFacetProvider, Locale.ROOT, 12);
		Field fieldConfig = new Field("tags").setFieldLevel(FieldLevel.MASTER).setType(FieldType.STRING);
		FacetConfig facetConfig = new FacetConfig("Tags", "tags").setType(CUSTOM_FACET_TYPE);
		underTest.addFacet(fieldConfig, facetConfig);

		// :: run
		Map<FacetCreatorClassifier, FacetCreator> facetCreators = underTest.init();
		assertDefaultFacetCreators(facetCreators);

		// :: verify
		FacetCreator customFacetCreator = facetCreators.get(new FacetCreatorClassifier(false, CUSTOM_FACET_TYPE));
		NestedCustomFacetCreator nestedCustomFC = assertInstanceOfAndGet(customFacetCreator, NestedCustomFacetCreator.class);

		// top level aggregation
		AggregationBuilder aggregation = nestedCustomFC.buildAggregation(null);
		// ensure it has a non-conflicting name
		assertEquals("CustomFacetAgg_" + CUSTOM_FACET_TYPE + "_m", aggregation.getName(), aggregation::toString);

		AggregationBuilder customSubAgg = aggregation
				.getSubAggregations().iterator().next() // filters agg
				.getSubAggregations().iterator().next() // names agg
				.getSubAggregations().iterator().next(); // custom agg
		assertEquals(CUSTOM_AGG_NAME, customSubAgg.getName(), customSubAgg::toString);

		TermFacetCreator masterTermFacetCreator = (TermFacetCreator) facetCreators.get(FacetCreatorClassifier.masterTermFacet);
		assertTrue(masterTermFacetCreator.getGeneralExcludedFields().contains(fieldConfig.getName()));
	}

	private <T> T assertInstanceOfAndGet(Object o, Class<T> clazz) {
		assertNotNull(o);
		assertTrue(clazz.isAssignableFrom(o.getClass()), () -> "expected to be of class " + clazz.getName() + " but was of " + o.getClass().getName());
		return clazz.cast(o);
	}

}
