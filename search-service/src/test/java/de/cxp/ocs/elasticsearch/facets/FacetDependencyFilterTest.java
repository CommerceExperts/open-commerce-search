package de.cxp.ocs.elasticsearch.facets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import de.cxp.ocs.config.FacetConfiguration.FacetConfig;
import de.cxp.ocs.config.Field;
import de.cxp.ocs.elasticsearch.model.filter.InternalResultFilter;
import de.cxp.ocs.elasticsearch.query.filter.FilterContext;
import de.cxp.ocs.elasticsearch.query.filter.PathResultFilter;
import de.cxp.ocs.elasticsearch.query.filter.TermResultFilter;
import de.cxp.ocs.model.result.Facet;

public class FacetDependencyFilterTest {

	private final FilterContext noFiltersContext = new FilterContext(Map.of(), Map.of());
	// only those parameters are considered as filter parameters in this test. Others are considered custom parameters
	private final Set<String>   knownFilterParameters = Set.of("brand", "category", "color", "size");

	@Test
	public void testNoConfigDoesNotRejectFacet() {
		FacetDependencyFilter underTest = new FacetDependencyFilter(Collections.emptyMap());
		boolean filterResult = underTest.isVisibleFacet(new Facet("n"), new FacetConfig(), noFiltersContext, 10);
		assertTrue(filterResult);
	}
	
	@Test
	public void testInvalidFacetDependency() {
		Map<String, FacetConfig> facetConfigs = new HashMap<>();

		FacetConfig facet1Config = new FacetConfig("Facet 1", "f1").setFilterDependencies("brand=&category=");
		facetConfigs.put(facet1Config.getSourceField(), facet1Config);

		FacetConfig facet2Config = new FacetConfig("Facet 2", "f2").setFilterDependencies("brand=foo=bar");
		facetConfigs.put(facet2Config.getSourceField(), facet2Config);

		FacetDependencyFilter underTest = new FacetDependencyFilter(facetConfigs);

		Facet facet1 = new Facet(facet1Config.getSourceField());
		assertTrue(underTest.isVisibleFacet(facet1, facet1Config, noFiltersContext, 0));
		assertTrue(underTest.isVisibleFacet(facet1, facet1Config, contextOfFilters("brand=X"), 0));

		Facet facet2 = new Facet(facet1Config.getSourceField());
		assertTrue(underTest.isVisibleFacet(facet2, facet2Config, noFiltersContext, 0));
		assertTrue(underTest.isVisibleFacet(facet2, facet2Config, contextOfFilters("brand=foo"), 0));
	}
	
	
	@Test
	public void testSingleParameterDependency() {
		FacetConfig sizeFacetConfig = new FacetConfig("Size", "size").setFilterDependencies("brand=FooBar");
		Map<String, FacetConfig> facetConfigs = Collections.singletonMap("size", sizeFacetConfig);
		FacetDependencyFilter underTest = new FacetDependencyFilter(facetConfigs);

		Facet sizeFacet = new Facet("size");
		assertFalse(underTest.isVisibleFacet(sizeFacet, sizeFacetConfig, noFiltersContext, 0));
		assertTrue(underTest.isVisibleFacet(new Facet("other"), new FacetConfig("OTHER", "other"), noFiltersContext, 0));

		assertFalse(underTest.isVisibleFacet(sizeFacet, sizeFacetConfig, contextOfFilters("brand=Blub"), 0));
		assertFalse(underTest.isVisibleFacet(sizeFacet, sizeFacetConfig, contextOfFilters("brand=Foo"), 0));
		assertTrue(underTest.isVisibleFacet(sizeFacet, sizeFacetConfig, contextOfFilters("brand=FooBar"), 0));
		assertTrue(underTest.isVisibleFacet(sizeFacet, sizeFacetConfig, contextOfFilters("brand=foobar"), 0));
		assertTrue(underTest.isVisibleFacet(sizeFacet, sizeFacetConfig, contextOfFilters("brand=any,foobar"), 0));
		assertTrue(underTest.isVisibleFacet(sizeFacet, sizeFacetConfig, contextOfFilters("brand=any,foobar", "category=x"), 0));
	}

	@Test
	public void testSingleWildcardDependency() {
		FacetConfig sizeFacetConfig = new FacetConfig("Size", "size").setFilterDependencies("category=*");
		Map<String, FacetConfig> facetConfigs = Collections.singletonMap("size", sizeFacetConfig);
		FacetDependencyFilter underTest = new FacetDependencyFilter(facetConfigs);

		Facet sizeFacet = new Facet("size");
		assertFalse(underTest.isVisibleFacet(sizeFacet, sizeFacetConfig, noFiltersContext, 0));
		assertTrue(underTest.isVisibleFacet(sizeFacet, sizeFacetConfig, contextOfFilters("category=X"), 0));
		assertTrue(underTest.isVisibleFacet(sizeFacet, sizeFacetConfig, contextOfFilters("category=Y"), 0));
		assertTrue(underTest.isVisibleFacet(sizeFacet, sizeFacetConfig, contextOfFilters("category=Y", "brand=x"), 0));
	}

	@Test
	public void testPathFilterDependency() {
		FacetConfig sizeFacetConfig = new FacetConfig("Size", "size").setFilterDependencies("category=a/b");
		Map<String, FacetConfig> facetConfigs = Collections.singletonMap("size", sizeFacetConfig);
		FacetDependencyFilter underTest = new FacetDependencyFilter(facetConfigs);

		Facet sizeFacet = new Facet("size");
		assertFalse(underTest.isVisibleFacet(sizeFacet, sizeFacetConfig, contextOfFilters("category=a"), 0));
		assertTrue(underTest.isVisibleFacet(sizeFacet, sizeFacetConfig, contextOfFilters("category=a/b"), 0));
		assertTrue(underTest.isVisibleFacet(sizeFacet, sizeFacetConfig, contextOfFilters("category=a/b/c"), 0));
		assertTrue(underTest.isVisibleFacet(sizeFacet, sizeFacetConfig, contextOfFilters("category=a/b/c", "brand=x"), 0));

		assertFalse(underTest.isVisibleFacet(sizeFacet, sizeFacetConfig, contextOfFilters("category=a,b"), 0));
		assertFalse(underTest.isVisibleFacet(sizeFacet, sizeFacetConfig, contextOfFilters("category=a/bee"), 0));
		assertTrue(underTest.isVisibleFacet(sizeFacet, sizeFacetConfig, contextOfFilters("category=x,a/b"), 0));
		assertTrue(underTest.isVisibleFacet(sizeFacet, sizeFacetConfig, contextOfFilters("category=a/b,x"), 0));
		assertTrue(underTest.isVisibleFacet(sizeFacet, sizeFacetConfig, contextOfFilters("category=a/b/c,x"), 0));
	}

	@Test
	public void testMultiValueFilterDependency() {
		FacetConfig sizeFacetConfig = new FacetConfig("Size", "size").setFilterDependencies("color=red,black");
		Map<String, FacetConfig> facetConfigs = Collections.singletonMap("size", sizeFacetConfig);
		FacetDependencyFilter underTest = new FacetDependencyFilter(facetConfigs);

		Facet sizeFacet = new Facet("size");
		assertFalse(underTest.isVisibleFacet(sizeFacet, sizeFacetConfig, contextOfFilters("category=a"), 0));
		assertFalse(underTest.isVisibleFacet(sizeFacet, sizeFacetConfig, contextOfFilters("color=black"), 0));
		assertFalse(underTest.isVisibleFacet(sizeFacet, sizeFacetConfig, contextOfFilters("color=red"), 0));

		assertTrue(underTest.isVisibleFacet(sizeFacet, sizeFacetConfig, contextOfFilters("color=black,red"), 0));
		assertTrue(underTest.isVisibleFacet(sizeFacet, sizeFacetConfig, contextOfFilters("color=red,black"), 0));
		assertTrue(underTest.isVisibleFacet(sizeFacet, sizeFacetConfig, contextOfFilters("color=red,black,yellow"), 0));
		assertTrue(underTest.isVisibleFacet(sizeFacet, sizeFacetConfig, contextOfFilters("color=red,yellow,black"), 0));
		assertTrue(underTest.isVisibleFacet(sizeFacet, sizeFacetConfig, contextOfFilters("color=yellow,red,black"), 0));
	}

	@Test
	public void testMultipleAlternativeDependencies() {
		FacetConfig sizeFacetConfig = new FacetConfig("Size", "size").setFilterDependencies("category=a/b", "category=c/d");
		Map<String, FacetConfig> facetConfigs = Collections.singletonMap("size", sizeFacetConfig);
		FacetDependencyFilter underTest = new FacetDependencyFilter(facetConfigs);
		Facet sizeFacet = new Facet("size");

		assertFalse(underTest.isVisibleFacet(sizeFacet, sizeFacetConfig, contextOfFilters("category=a"), 0));
		// path-filters are case sensitive
		assertFalse(underTest.isVisibleFacet(sizeFacet, sizeFacetConfig, contextOfFilters("category=A/B"), 0));
		assertTrue(underTest.isVisibleFacet(sizeFacet, sizeFacetConfig, contextOfFilters("category=a/b"), 0));

		assertFalse(underTest.isVisibleFacet(sizeFacet, sizeFacetConfig, contextOfFilters("category=c"), 0));
		assertFalse(underTest.isVisibleFacet(sizeFacet, sizeFacetConfig, contextOfFilters("category=a/d"), 0));

		assertTrue(underTest.isVisibleFacet(sizeFacet, sizeFacetConfig, contextOfFilters("category=c/d"), 0));
		assertTrue(underTest.isVisibleFacet(sizeFacet, sizeFacetConfig, contextOfFilters("category=a/b/c/d"), 0));
		assertTrue(underTest.isVisibleFacet(sizeFacet, sizeFacetConfig, contextOfFilters("category=c/d/a/b"), 0));
		assertTrue(underTest.isVisibleFacet(sizeFacet, sizeFacetConfig, contextOfFilters("category=c/d,a/b"), 0));
	}
	
	@Test
	public void testMultipleDifferentAlternativeDependencies() {
		FacetConfig sizeFacetConfig = new FacetConfig("Size", "size").setFilterDependencies("category=a/b", "brand=x");
		Map<String, FacetConfig> facetConfigs = Collections.singletonMap("size", sizeFacetConfig);
		FacetDependencyFilter underTest = new FacetDependencyFilter(facetConfigs);
		Facet sizeFacet = new Facet("size");

		assertFalse(underTest.isVisibleFacet(sizeFacet, sizeFacetConfig, contextOfFilters("category=a"), 0));
		assertFalse(underTest.isVisibleFacet(sizeFacet, sizeFacetConfig, contextOfFilters("brand=a"), 0));
		
		assertTrue(underTest.isVisibleFacet(sizeFacet, sizeFacetConfig, contextOfFilters("category=a/b"), 0));
		assertTrue(underTest.isVisibleFacet(sizeFacet, sizeFacetConfig, contextOfFilters("brand=x"), 0));
		assertTrue(underTest.isVisibleFacet(sizeFacet, sizeFacetConfig, contextOfFilters("brand=x", "category=a/b"), 0));
		assertTrue(underTest.isVisibleFacet(sizeFacet, sizeFacetConfig, contextOfFilters("brand=x", "category=c"), 0));
		assertTrue(underTest.isVisibleFacet(sizeFacet, sizeFacetConfig, contextOfFilters("brand=y", "category=a/b"), 0));
	}

	@Test
	public void testCombinedDependency() {
		FacetConfig sizeFacetConfig = new FacetConfig("Size", "size").setFilterDependencies("category=a/b&brand=x");
		Map<String, FacetConfig> facetConfigs = Collections.singletonMap("size", sizeFacetConfig);
		FacetDependencyFilter underTest = new FacetDependencyFilter(facetConfigs);
		Facet sizeFacet = new Facet("size");

		assertFalse(underTest.isVisibleFacet(sizeFacet, sizeFacetConfig, contextOfFilters("category=a"), 0));
		assertFalse(underTest.isVisibleFacet(sizeFacet, sizeFacetConfig, contextOfFilters("category=a/b"), 0));
		assertFalse(underTest.isVisibleFacet(sizeFacet, sizeFacetConfig, contextOfFilters("brand=x"), 0));
		
		assertFalse(underTest.isVisibleFacet(sizeFacet, sizeFacetConfig, contextOfFilters("brand=x", "category=c/d"), 0));
		assertFalse(underTest.isVisibleFacet(sizeFacet, sizeFacetConfig, contextOfFilters("brand=y", "category=a/b"), 0));
		
		assertTrue(underTest.isVisibleFacet(sizeFacet, sizeFacetConfig, contextOfFilters("category=a/b", "brand=x"), 0));
		assertTrue(underTest.isVisibleFacet(sizeFacet, sizeFacetConfig, contextOfFilters("brand=x", "category=a/b"), 0));
		assertTrue(underTest.isVisibleFacet(sizeFacet, sizeFacetConfig, contextOfFilters("brand=x", "category=a/b", "color=red"), 0));
	}

	@Test
	public void testMultipleCombinedDependencies() {
		FacetConfig sizeFacetConfig = new FacetConfig("Size", "size")
				.setFilterDependencies("category=a/b&brand=x", "category=c&brand=y");
		Map<String, FacetConfig> facetConfigs = Collections.singletonMap("size", sizeFacetConfig);
		FacetDependencyFilter underTest = new FacetDependencyFilter(facetConfigs);
		Facet sizeFacet = new Facet("size");
		
		assertFalse(underTest.isVisibleFacet(sizeFacet, sizeFacetConfig, contextOfFilters("category=a"), 0));
		assertFalse(underTest.isVisibleFacet(sizeFacet, sizeFacetConfig, contextOfFilters("category=a/b"), 0));
		assertFalse(underTest.isVisibleFacet(sizeFacet, sizeFacetConfig, contextOfFilters("brand=y"), 0));
		
		assertFalse(underTest.isVisibleFacet(sizeFacet, sizeFacetConfig, contextOfFilters("category=a/b", "brand=y"), 0));
		assertFalse(underTest.isVisibleFacet(sizeFacet, sizeFacetConfig, contextOfFilters("brand=x", "category=c"), 0));
		assertFalse(underTest.isVisibleFacet(sizeFacet, sizeFacetConfig, contextOfFilters("brand=x", "brand=y"), 0));
		assertFalse(underTest.isVisibleFacet(sizeFacet, sizeFacetConfig, contextOfFilters("brand=x,y"), 0));
		
		assertTrue(underTest.isVisibleFacet(sizeFacet, sizeFacetConfig, contextOfFilters("category=a/b", "brand=x"), 0));
		assertTrue(underTest.isVisibleFacet(sizeFacet, sizeFacetConfig, contextOfFilters("category=a/b", "brand=x,y"), 0));
		assertTrue(underTest.isVisibleFacet(sizeFacet, sizeFacetConfig, contextOfFilters("category=a/b/c", "brand=x,y"), 0));
		assertTrue(underTest.isVisibleFacet(sizeFacet, sizeFacetConfig, contextOfFilters("category=c", "brand=x,y"), 0));
		assertTrue(underTest.isVisibleFacet(sizeFacet, sizeFacetConfig, contextOfFilters("category=a/b,c", "brand=x,y"), 0));
		assertTrue(underTest.isVisibleFacet(sizeFacet, sizeFacetConfig, contextOfFilters("category=a/bee,c", "brand=x,y"), 0));
		assertTrue(underTest.isVisibleFacet(sizeFacet, sizeFacetConfig, contextOfFilters("category=a/b/d,c", "brand=x,y"), 0));
	}

	@Test
	public void testCombinedWildcardDependency() {
		FacetConfig sizeFacetConfig = new FacetConfig("Size", "size").setFilterDependencies("category=*&brand=*");
		Map<String, FacetConfig> facetConfigs = Collections.singletonMap("size", sizeFacetConfig);
		FacetDependencyFilter underTest = new FacetDependencyFilter(facetConfigs);
		Facet sizeFacet = new Facet("size");

		assertFalse(underTest.isVisibleFacet(sizeFacet, sizeFacetConfig, contextOfFilters("category=a"), 0));
		assertFalse(underTest.isVisibleFacet(sizeFacet, sizeFacetConfig, contextOfFilters("brand=y"), 0));
		assertTrue(underTest.isVisibleFacet(sizeFacet, sizeFacetConfig, contextOfFilters("category=a/b", "brand=y"), 0));
	}

	@Test
	public void testMultiplCombinedWildcardDependencies() {
		FacetConfig sizeFacetConfig = new FacetConfig("Size", "size")
				.setFilterDependencies("category=a&brand=*", "category=*&brand=y");
		Map<String, FacetConfig> facetConfigs = Collections.singletonMap("size", sizeFacetConfig);
		FacetDependencyFilter underTest = new FacetDependencyFilter(facetConfigs);
		Facet sizeFacet = new Facet("size");

		assertFalse(underTest.isVisibleFacet(sizeFacet, sizeFacetConfig, contextOfFilters("category=a"), 0));
		assertFalse(underTest.isVisibleFacet(sizeFacet, sizeFacetConfig, contextOfFilters("brand=y"), 0));
		assertFalse(underTest.isVisibleFacet(sizeFacet, sizeFacetConfig, contextOfFilters("category=c", "brand=x"), 0));

		assertTrue(underTest.isVisibleFacet(sizeFacet, sizeFacetConfig, contextOfFilters("category=a/b", "brand=y"), 0));
		assertTrue(underTest.isVisibleFacet(sizeFacet, sizeFacetConfig, contextOfFilters("category=c", "brand=y"), 0));
		assertTrue(underTest.isVisibleFacet(sizeFacet, sizeFacetConfig, contextOfFilters("category=a", "brand=x"), 0));
		assertTrue(underTest.isVisibleFacet(sizeFacet, sizeFacetConfig, contextOfFilters("category=a,c", "brand=x,y"), 0));
	}

	@Test
	public void testCustomParameterDependency() {
		FacetConfig specialConfig = new FacetConfig("Special Category", "category_x")
				.setFilterDependencies("withSpecialCategory=true");
		Map<String, FacetConfig> facetConfigs = Collections.singletonMap("category_x", specialConfig);
		FacetDependencyFilter underTest = new FacetDependencyFilter(facetConfigs);
		Facet specialFacet = new Facet("category_x");

		assertFalse(underTest.isVisibleFacet(specialFacet, specialConfig, contextOfFilters("category=a"), 0));
		assertFalse(underTest.isVisibleFacet(specialFacet, specialConfig, contextOfFilters("brand=y"), 0));
		assertFalse(underTest.isVisibleFacet(specialFacet, specialConfig, contextOfFilters("withSpecialCategory=1"), 0));
		assertFalse(underTest.isVisibleFacet(specialFacet, specialConfig, contextOfFilters("withSpecialCategory=false", "other=true"), 0));

		// custom parameters are not parsed like filters, so the filter value syntax (negate, comma) are not considered
		assertFalse(underTest.isVisibleFacet(specialFacet, specialConfig, contextOfFilters("withSpecialCategory=true,valid"), 0));
		assertFalse(underTest.isVisibleFacet(specialFacet, specialConfig, contextOfFilters("withSpecialCategory=!true"), 0));

		assertTrue(underTest.isVisibleFacet(specialFacet, specialConfig, contextOfFilters("withSpecialCategory=true"), 0));
		assertTrue(underTest.isVisibleFacet(specialFacet, specialConfig, contextOfFilters("withSpecialCategory=true", "param=any"), 0));
		assertTrue(underTest.isVisibleFacet(specialFacet, specialConfig, contextOfFilters("category=c", "withSpecialCategory=true"), 0));

	}

	@Test
	public void testFilterAndCustomParameterDependency() {
		FacetConfig specialConfig = new FacetConfig("Special Category", "category_x")
				.setFilterDependencies("withSpecialCategory=true&brand=x");
		Map<String, FacetConfig> facetConfigs = Collections.singletonMap("category_x", specialConfig);
		FacetDependencyFilter underTest = new FacetDependencyFilter(facetConfigs);
		Facet specialFacet = new Facet("category_x");

		assertFalse(underTest.isVisibleFacet(specialFacet, specialConfig, contextOfFilters("category=a"), 0));
		assertFalse(underTest.isVisibleFacet(specialFacet, specialConfig, contextOfFilters("brand=x"), 0));
		assertFalse(underTest.isVisibleFacet(specialFacet, specialConfig, contextOfFilters("withSpecialCategory=true"), 0));
		assertFalse(underTest.isVisibleFacet(specialFacet, specialConfig, contextOfFilters("withSpecialCategory=false", "brand=x"), 0));

		// make sure order does not matter
		assertTrue(underTest.isVisibleFacet(specialFacet, specialConfig, contextOfFilters("brand=x", "withSpecialCategory=true"), 0));
		assertTrue(underTest.isVisibleFacet(specialFacet, specialConfig, contextOfFilters("withSpecialCategory=true", "brand=x"), 0));

		// make sure combination with other parameters does not matter
		assertTrue(underTest.isVisibleFacet(specialFacet, specialConfig, contextOfFilters("category=c", "withSpecialCategory=true", "brand=x"), 0));
		assertTrue(underTest.isVisibleFacet(specialFacet, specialConfig, contextOfFilters("brand=x", "category=c", "withSpecialCategory=true"), 0));
		assertTrue(underTest.isVisibleFacet(specialFacet, specialConfig, contextOfFilters("brand=x,y", "category=c", "withSpecialCategory=true"), 0));
	}

	private FilterContext contextOfFilters(String... params) {
		Map<String, InternalResultFilter> internalFilters = new HashMap<>();
		Map<String, String> customParameters = new HashMap<>();
		for (String param : params) {
			String[] paramSplit = param.split("=");

			if (!knownFilterParameters.contains(paramSplit[0])) {
				customParameters.put(paramSplit[0], paramSplit[1]);
			}
			else if (paramSplit[1].contains("/")) {
				internalFilters.put(paramSplit[0],
						new PathResultFilter(new Field(paramSplit[0]), paramSplit[1].split(",")));
			} else {
				internalFilters.put(paramSplit[0],
						new TermResultFilter(new Field(paramSplit[0]), paramSplit[1].split(",")));
			}
		}
		return new FilterContext(internalFilters, customParameters);
	}
}
