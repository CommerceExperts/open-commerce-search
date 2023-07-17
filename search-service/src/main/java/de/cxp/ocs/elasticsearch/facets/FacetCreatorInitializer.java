package de.cxp.ocs.elasticsearch.facets;

import static de.cxp.ocs.elasticsearch.facets.FacetCreatorClassifier.hierarchicalFacet;
import static de.cxp.ocs.elasticsearch.facets.FacetCreatorClassifier.masterIntervalFacet;
import static de.cxp.ocs.elasticsearch.facets.FacetCreatorClassifier.masterRangeFacet;
import static de.cxp.ocs.elasticsearch.facets.FacetCreatorClassifier.masterTermFacet;
import static de.cxp.ocs.elasticsearch.facets.FacetCreatorClassifier.variantIntervalFacet;
import static de.cxp.ocs.elasticsearch.facets.FacetCreatorClassifier.variantRangeFacet;
import static de.cxp.ocs.elasticsearch.facets.FacetCreatorClassifier.variantTermFacet;

import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import de.cxp.ocs.config.FacetConfiguration.FacetConfig;
import de.cxp.ocs.config.FacetType;
import de.cxp.ocs.config.Field;
import de.cxp.ocs.config.FieldType;
import de.cxp.ocs.config.SearchConfiguration;
import de.cxp.ocs.spi.search.CustomFacetCreator;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
class FacetCreatorInitializer {

	@NonNull
	private final Map<String, Supplier<? extends CustomFacetCreator>>	customFacetCreators;
	@NonNull
	private final Function<String, FacetConfig>							defaultTermFacetConfigProvider;
	private final Function<String, FacetConfig>							defaultNumberFacetConfigProvider;

	private final int													maxFacets;
	private final Locale	locale;

	public FacetCreatorInitializer(Map<String, Supplier<? extends CustomFacetCreator>> customFacetCreators, SearchConfiguration config, Function<String, FacetConfig> defaultTermFacetConfigProvider, Function<String, FacetConfig> defaultNumberFacetConfigProvider) {
		this.customFacetCreators = customFacetCreators;
		this.defaultTermFacetConfigProvider = defaultTermFacetConfigProvider;
		this.defaultNumberFacetConfigProvider = defaultNumberFacetConfigProvider;
		maxFacets = config.getFacetConfiguration().getMaxFacets();
		locale = config.getLocale();
	}

	public FacetCreatorInitializer(Map<String, Supplier<? extends CustomFacetCreator>> customFacetCreators, Function<String, FacetConfig> defaultTermFacetConfigProvider, Function<String, FacetConfig> defaultNumberFacetConfigProvider, Locale locale, int maxFacets) {
		this.customFacetCreators = customFacetCreators;
		this.defaultTermFacetConfigProvider = defaultTermFacetConfigProvider;
		this.defaultNumberFacetConfigProvider = defaultNumberFacetConfigProvider;
		this.maxFacets = maxFacets;
		this.locale = locale;
	}


	@RequiredArgsConstructor
	private class ConfigCollector {

		Map<String, FacetConfig>	standardConfigs	= new HashMap<>();
		Map<String, FacetConfig>	customConfigs	= new HashMap<>();
	}

	Map<FacetCreatorClassifier, ConfigCollector>	collectedConfigs	= new HashMap<>();
	Set<Field>									ignoredFields		= new HashSet<>();

	void addFacet(Field field, FacetConfig facetConfig) {

		if (facetConfig.getType() == null) {
			String defaultFacetTypeForField = getDefaultFacetType(field.getType()).name();
			facetConfig.setType(defaultFacetTypeForField);
			log.info("set default facet type {} for facet {} because of related field type {}", defaultFacetTypeForField, facetConfig.getLabel(), field.getType());
		}
		else if ("ignore".equalsIgnoreCase(facetConfig.getType())) {
			ignoredFields.add(field);
			return;
		}

		CustomFacetCreator customFacetCreator = Optional.ofNullable(customFacetCreators.get(facetConfig.getType())).map(Supplier::get).orElse(null);

		if (customFacetCreator != null) {
			if (field.getType().equals(customFacetCreator.getAcceptibleFieldType())) {
				addValidatedCustomFacet(field, facetConfig);
			}
			else {
				log.warn("Facet {} for field {} with custom type {} is not compatible with field-type {}! Ignorning it.", facetConfig.getLabel(), facetConfig.getSourceField(), facetConfig.getType(), field.getType());
			}
		}
		else {
			try {
				FacetType facetType = FacetType.valueOf(facetConfig.getType().toUpperCase());
				if (!isCompatibleTypes(field.getType(), facetType)) {
					log.warn("Facet {} for field {} with type {} is not compatible with field-type {}! Ignorning it.", facetConfig.getLabel(), facetConfig.getSourceField(), facetConfig.getType(), field.getType());
				}
				else if (FacetType.HIERARCHICAL.equals(facetType) && field.isVariantLevel()) {
					log.warn("Facet {} for field {} with type {} does not work on variant level! Ignorning it.", facetConfig.getLabel(), facetConfig.getSourceField(), facetConfig.getType());
				}
				else {
					addValidatedFacet(field, facetConfig);
				}
			}
			catch (Exception e) {
				log.error("unknown facet type {} can not be handled: Not standard type and no custom facet creator found", facetConfig.getType());
			}
		}
	}

	private void addValidatedFacet(Field facetField, FacetConfig facetConfig) {
		if (facetField.isVariantLevel()) {
			_addValidatedFacet(facetField, true, facetConfig, c -> c.standardConfigs);
		}
		if (facetField.isMasterLevel()) {
			_addValidatedFacet(facetField, false, facetConfig, c -> c.standardConfigs);
		}
	}

	private void addValidatedCustomFacet(Field facetField, FacetConfig facetConfig) {
		if (facetField.isVariantLevel()) {
			_addValidatedFacet(facetField, true, facetConfig, c -> c.customConfigs);
		}
		if (facetField.isMasterLevel()) {
			_addValidatedFacet(facetField, false, facetConfig, c -> c.customConfigs);
		}
	}

	private void _addValidatedFacet(Field facetField, boolean variant, FacetConfig facetConfig, Function<ConfigCollector, Map<String, FacetConfig>> collectorTargetMap) {
		ConfigCollector configCollector = collectedConfigs.computeIfAbsent(new FacetCreatorClassifier(variant, facetConfig.getType()), k -> new ConfigCollector());
		collectorTargetMap.apply(configCollector).put(facetField.getName(), facetConfig);
	}

	private FacetType getDefaultFacetType(FieldType type) {
		switch (type) {
			case NUMBER:
				return FacetType.INTERVAL;
			case CATEGORY:
				return FacetType.HIERARCHICAL;
			case COMBI:
			case ID:
			case STRING:
			default:
				return FacetType.TERM;
		}
	}

	private boolean isCompatibleTypes(FieldType fieldType, FacetType facetType) {
		switch (fieldType) {
			case NUMBER:
				return FacetType.INTERVAL.equals(facetType) || FacetType.RANGE.equals(facetType);
			case CATEGORY:
				return FacetType.HIERARCHICAL.equals(facetType);
			default:
				return FacetType.TERM.equals(facetType);
		}
	}

	Map<FacetCreatorClassifier, FacetCreator> init() {
		Map<FacetCreatorClassifier, FacetCreator> facetCreatorsByTypes = new HashMap<>();

		// build all generic facet creators passing the specific configs to it
		CategoryFacetCreator categoryFacetCreator = new CategoryFacetCreator(getStandardConfigs(hierarchicalFacet), null);
		categoryFacetCreator.setGeneralExcludedFields(getNamesOfMatchingFields(ignoredFields, FieldType.CATEGORY));
		facetCreatorsByTypes.put(hierarchicalFacet, categoryFacetCreator);

		NestedFacetCreator masterTermFacetCreator = new TermFacetCreator(getStandardConfigs(masterTermFacet), defaultTermFacetConfigProvider, locale)
				.setMaxFacets(maxFacets);
		masterTermFacetCreator.setGeneralExcludedFields(getNamesOfMatchingFields(ignoredFields, FieldType.STRING));
		facetCreatorsByTypes.put(masterTermFacet, masterTermFacetCreator);


		String defaultNumberFacetType = defaultNumberFacetConfigProvider.apply("").getType();
		initNumberFacetCreators(facetCreatorsByTypes, getStandardConfigs(masterIntervalFacet), getStandardConfigs(masterRangeFacet), ignoredFields, defaultNumberFacetType, false);

		NestedFacetCreator variantTermFacetCreator = new TermFacetCreator(getStandardConfigs(variantTermFacet), defaultTermFacetConfigProvider, locale).setMaxFacets(maxFacets);
		variantTermFacetCreator.setGeneralExcludedFields(getNamesOfMatchingFields(ignoredFields, FieldType.STRING));
		facetCreatorsByTypes.put(variantTermFacet, new VariantFacetCreator(Collections.singleton(variantTermFacetCreator)));

		initNumberFacetCreators(facetCreatorsByTypes, getStandardConfigs(variantIntervalFacet), getStandardConfigs(variantRangeFacet), ignoredFields, defaultNumberFacetType, true);

		// TODO: init custom facet creators

		return facetCreatorsByTypes;
	}

	private Map<String, FacetConfig> getStandardConfigs(FacetCreatorClassifier type) {
		return Optional.ofNullable(collectedConfigs.get(type)).map(cc -> cc.standardConfigs).orElse(Collections.emptyMap());
	}

	/**
	 * Depending on the default number facet type, we also have to init the creator for the other type if there are
	 * specific configs for it. We then also have to exclude the fields for the specific creator from the default
	 * creator. And of course those creators have to be registered accordingly with the correct type.
	 * That all is handled here.
	 * 
	 * @param intervalFacetConfigs
	 *        either the ones from master or variant level
	 * @param rangeFacetConfigs
	 *        either the ones from master or variant level
	 * @param allIgnoredFields
	 *        a set of all ignored facet-fields
	 * @param defaultNumberFacetType
	 *        the type of the default numeric facet
	 * @param isVariantLevel
	 *        true if those configs are for variant level, otherwise false.
	 * @return
	 * @return
	 */
	private void initNumberFacetCreators(Map<FacetCreatorClassifier, FacetCreator> facetCreatorsByTypes, Map<String, FacetConfig> intervalFacetConfigs, Map<String, FacetConfig> rangeFacetConfigs, Set<Field> allIgnoredFields, String defaultNumberFacetType,
			boolean isVariantLevel) {

		NestedFacetCreator defaultNumberFacetCreator;
		// set for all non-default facets that should be excluded from default facet generation
		HashSet<String> nonDefaultNumberFacetFields;

		if (FacetType.RANGE.name().equals(defaultNumberFacetType)) {
			NestedFacetCreator rangeFacetCreator = new RangeFacetCreator(rangeFacetConfigs, defaultNumberFacetConfigProvider).setMaxFacets(maxFacets);
			if (isVariantLevel) {
				facetCreatorsByTypes.put(FacetCreatorClassifier.variantRangeFacet, new VariantFacetCreator(Collections.singleton(rangeFacetCreator)));
			}
			else {
				facetCreatorsByTypes.put(FacetCreatorClassifier.masterRangeFacet, rangeFacetCreator);
			}

			defaultNumberFacetCreator = rangeFacetCreator;
			nonDefaultNumberFacetFields = new HashSet<>(intervalFacetConfigs.keySet());

			// add facet creator for explicit facet creation that has different type than the default
			if (!intervalFacetConfigs.isEmpty()) {
				IntervalFacetCreator intervalFacetCreator = new IntervalFacetCreator(intervalFacetConfigs, null);
				intervalFacetCreator.setExplicitFacetCreator(true);
				intervalFacetCreator.setMaxFacets(maxFacets);
				intervalFacetCreator.setGeneralExcludedFields(getNamesOfMatchingFields(allIgnoredFields, FieldType.NUMBER));
				if (isVariantLevel) {
					facetCreatorsByTypes.put(FacetCreatorClassifier.variantIntervalFacet, new VariantFacetCreator(Collections.singleton(intervalFacetCreator)));
				}
				else {
					facetCreatorsByTypes.put(FacetCreatorClassifier.masterIntervalFacet, intervalFacetCreator);
				}
			}
		}
		else {
			if (!FacetType.INTERVAL.name().equals(defaultNumberFacetType)) {
				log.error("Invalid type for default number facet configuration: '{}' - will consider 'INTERVAL' as default", defaultNumberFacetType);
			}
			NestedFacetCreator intervalFacetCreator = new IntervalFacetCreator(intervalFacetConfigs, defaultNumberFacetConfigProvider).setMaxFacets(maxFacets);
			if (isVariantLevel) {
				facetCreatorsByTypes.put(FacetCreatorClassifier.variantIntervalFacet, new VariantFacetCreator(Collections.singleton(intervalFacetCreator)));
			}
			else {
				facetCreatorsByTypes.put(FacetCreatorClassifier.masterIntervalFacet, intervalFacetCreator);
			}

			defaultNumberFacetCreator = intervalFacetCreator;
			nonDefaultNumberFacetFields = new HashSet<>(rangeFacetConfigs.keySet());

			// add facet creator for explicit facet creation that has different type than the default
			if (!rangeFacetConfigs.isEmpty()) {
				// TODO: FacetCreators that run on the same nested field, should be
				// grouped to use a single nested-aggregation for their aggregations
				RangeFacetCreator rangeFacetCreator = new RangeFacetCreator(rangeFacetConfigs, null);
				rangeFacetCreator.setExplicitFacetCreator(true);
				rangeFacetCreator.setMaxFacets(maxFacets);
				rangeFacetCreator.setGeneralExcludedFields(getNamesOfMatchingFields(allIgnoredFields, FieldType.NUMBER));

				if (isVariantLevel) {
					facetCreatorsByTypes.put(FacetCreatorClassifier.variantRangeFacet, new VariantFacetCreator(Collections.singleton(rangeFacetCreator)));
				}
				else {
					facetCreatorsByTypes.put(FacetCreatorClassifier.masterRangeFacet, rangeFacetCreator);
				}
			}

		}

		nonDefaultNumberFacetFields.addAll(getNamesOfMatchingFields(allIgnoredFields, FieldType.NUMBER));
		defaultNumberFacetCreator.setGeneralExcludedFields(nonDefaultNumberFacetFields);
	}

	private Set<String> getNamesOfMatchingFields(Set<Field> ignoredFields, FieldType fieldType) {
		return ignoredFields.stream()
				.filter(f -> fieldType.equals(f.getType()))
				.map(Field::getName)
				.collect(Collectors.toSet());
	}

}
