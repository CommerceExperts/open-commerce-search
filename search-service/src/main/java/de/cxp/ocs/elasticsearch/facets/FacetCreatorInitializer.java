package de.cxp.ocs.elasticsearch.facets;

import static de.cxp.ocs.elasticsearch.facets.FacetCreatorClassifier.hierarchicalFacet;
import static de.cxp.ocs.elasticsearch.facets.FacetCreatorClassifier.masterIntervalFacet;
import static de.cxp.ocs.elasticsearch.facets.FacetCreatorClassifier.masterRangeFacet;
import static de.cxp.ocs.elasticsearch.facets.FacetCreatorClassifier.masterTermFacet;
import static de.cxp.ocs.elasticsearch.facets.FacetCreatorClassifier.variantIntervalFacet;
import static de.cxp.ocs.elasticsearch.facets.FacetCreatorClassifier.variantRangeFacet;
import static de.cxp.ocs.elasticsearch.facets.FacetCreatorClassifier.variantTermFacet;

import java.util.*;
import java.util.Map.Entry;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import com.google.common.collect.Streams;

import de.cxp.ocs.config.*;
import de.cxp.ocs.config.FacetConfiguration.FacetConfig;
import de.cxp.ocs.spi.search.CustomFacetCreator;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
class FacetCreatorInitializer {

	@NonNull
	private final Map<String, Supplier<? extends CustomFacetCreator>>	customFacetCreatorSupplier;
	@NonNull
	private final Function<String, FacetConfig>							defaultTermFacetConfigProvider;
	private final Function<String, FacetConfig>							defaultNumberFacetConfigProvider;

	private final int maxFacets;

	@RequiredArgsConstructor
	private class ConfigCollector {

		FieldType					relatedFieldType;
		Map<String, FacetConfig>	configsByField	= new HashMap<>();

		FacetConfig putFacetConfig(String fieldName, FacetConfig facetConfig) {
			return configsByField.put(fieldName, facetConfig);
		}

		public ConfigCollector setFieldType(FieldType type) {
			if (relatedFieldType != null && !relatedFieldType.equals(type)) {
				throw new IllegalStateException("This should not be possible to use the same facet creator for different field types ('" + relatedFieldType + "' and '" + type + "')!");
			}
			relatedFieldType = type;
			return this;
		}
	}

	private final Map<FacetCreatorClassifier, ConfigCollector>		collectedConfigs	= new HashMap<>();
	private final Set<Field>										ignoredFields		= new HashSet<>();
	private final Set<Field>										explicitFacetFields	= new HashSet<>();
	private final Map<FacetCreatorClassifier, CustomFacetCreator>	customFacetCreators	= new HashMap<>();
	private final Set<Field>										customFacetFields	= new HashSet<>();
	private final Locale											locale;

	public FacetCreatorInitializer(Map<String, Supplier<? extends CustomFacetCreator>> customFacetCreatorSupplier, SearchConfiguration config, Function<String, FacetConfig> defaultTermFacetConfigProvider, Function<String, FacetConfig> defaultNumberFacetConfigProvider) {
		this.customFacetCreatorSupplier = customFacetCreatorSupplier;
		this.defaultTermFacetConfigProvider = defaultTermFacetConfigProvider;
		this.defaultNumberFacetConfigProvider = defaultNumberFacetConfigProvider;
		maxFacets = getFacetFetchLimit(config.getFacetConfiguration());
		locale = config.getLocale();
	}

	private int getFacetFetchLimit(FacetConfiguration facetConfiguration) {
		return (int) (facetConfiguration.getMaxFacets() + facetConfiguration.getFacets().stream().filter(FacetConfig::isExcludeFromFacetLimit).count());
	}

	// for internal / testing usage
	FacetCreatorInitializer(Map<String, Supplier<? extends CustomFacetCreator>> customFacetCreatorSupplier, Function<String, FacetConfig> defaultTermFacetConfigProvider, Function<String, FacetConfig> defaultNumberFacetConfigProvider, Locale locale, int maxFacets) {
		this.customFacetCreatorSupplier = customFacetCreatorSupplier;
		this.defaultTermFacetConfigProvider = defaultTermFacetConfigProvider;
		this.defaultNumberFacetConfigProvider = defaultNumberFacetConfigProvider;
		this.maxFacets = maxFacets;
		this.locale = locale;
	}

	void addFacet(Field field, FacetConfig facetConfig) {
		if ("ignore".equalsIgnoreCase(facetConfig.getType())) {
			ignoredFields.add(field);
			return;
		}

		CustomFacetCreator customFacetCreator = Optional.ofNullable(customFacetCreatorSupplier.get(facetConfig.getType())).map(Supplier::get).orElse(null);

		if (customFacetCreator != null) {
			if (field.getType().equals(customFacetCreator.getAcceptibleFieldType())) {
				addValidatedFacet(field, facetConfig);
				customFacetFields.add(field);

				if (field.isMasterLevel()) {
					customFacetCreators.putIfAbsent(new FacetCreatorClassifier(false, facetConfig.getType(), true), customFacetCreator);
				}
				if (field.isVariantLevel()) {
					customFacetCreators.putIfAbsent(new FacetCreatorClassifier(true, facetConfig.getType(), true), customFacetCreator);
				}
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
			_addValidatedFacet(facetField, true, facetConfig);
		}
		if (facetField.isMasterLevel()) {
			_addValidatedFacet(facetField, false, facetConfig);
		}
	}

	private void _addValidatedFacet(Field facetField, boolean variant, FacetConfig facetConfig) {
		boolean isMandatoryFacet = facetConfig.isExcludeFromFacetLimit() && facetConfig.getMinFacetCoverage() == 0;
		if (isMandatoryFacet) explicitFacetFields.add(facetField);
		FacetCreatorClassifier facetCreatorClassifier = new FacetCreatorClassifier(variant, facetConfig.getType(), isMandatoryFacet);
		collectedConfigs.computeIfAbsent(facetCreatorClassifier, k -> new ConfigCollector())
				.setFieldType(facetField.getType())
				.putFacetConfig(facetField.getName(), facetConfig);
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
		CategoryFacetCreator categoryFacetCreator = new CategoryFacetCreator(getConfigs(hierarchicalFacet), null);
		categoryFacetCreator.setGeneralExcludedFields(getIgnoredFieldsOfType(FieldType.CATEGORY));
		facetCreatorsByTypes.put(hierarchicalFacet, categoryFacetCreator);

		NestedFacetCreator masterTermFacetCreator = new TermFacetCreator(getConfigs(masterTermFacet), defaultTermFacetConfigProvider, locale)
				.setMaxFacets(maxFacets);
		masterTermFacetCreator.setGeneralExcludedFields(getIgnoredFieldsOfType(FieldType.STRING));
		facetCreatorsByTypes.put(masterTermFacet, masterTermFacetCreator);

		String defaultNumberFacetType = defaultNumberFacetConfigProvider.apply("").getType();
		initNumberFacetCreators(facetCreatorsByTypes, getConfigs(masterIntervalFacet), getConfigs(masterRangeFacet), defaultNumberFacetType, false);

		NestedFacetCreator variantTermFacetCreator = new TermFacetCreator(getConfigs(variantTermFacet), defaultTermFacetConfigProvider, locale).setMaxFacets(maxFacets);
		variantTermFacetCreator.setGeneralExcludedFields(getIgnoredFieldsOfType(FieldType.STRING));
		facetCreatorsByTypes.put(variantTermFacet, new VariantFacetCreator(Collections.singleton(variantTermFacetCreator)));

		initNumberFacetCreators(facetCreatorsByTypes, getConfigs(variantIntervalFacet), getConfigs(variantRangeFacet), defaultNumberFacetType, true);

		// init explicit facet creators
		for (FacetType facetType : FacetType.values()) {
			// check for explicit variant and main-level facet creation
			for (boolean onVariantLevel : new boolean[] { true, false }) {
				if (onVariantLevel && FacetType.HIERARCHICAL.equals(facetType)) continue; // not supported

				FacetCreatorClassifier facetClassifier = new FacetCreatorClassifier(onVariantLevel, facetType.name(), true);
				Map<String, FacetConfig> explicitConfigs = getConfigs(facetClassifier);
				if (!explicitConfigs.isEmpty()) {
					FacetCreator explicitFacetCreator = initExplicitFacetCreator(facetType, explicitConfigs);
					// TODO: optimization: the inner variant facet creator could be attached to the generic
					// variantFacetCreator of the same type
					if (onVariantLevel) explicitFacetCreator = new VariantFacetCreator(Collections.singleton(explicitFacetCreator));
					facetCreatorsByTypes.put(facetClassifier, explicitFacetCreator);
				}
			}
		}

		// init custom facet creators
		for (Entry<FacetCreatorClassifier, CustomFacetCreator> customFacetCreatorEntry : customFacetCreators.entrySet()) {
			FacetCreatorClassifier facetClassifier = customFacetCreatorEntry.getKey();

			Map<String, FacetConfig> customFacetConfigs = getConfigs(facetClassifier);
			NestedCustomFacetCreator nestedCustomFacetCreator = new NestedCustomFacetCreator(customFacetConfigs, collectedConfigs.get(facetClassifier).relatedFieldType, facetClassifier.onVariantLevel, customFacetCreatorEntry.getValue());

			facetCreatorsByTypes.put(facetClassifier, nestedCustomFacetCreator);
		}

		return facetCreatorsByTypes;
	}

	private Map<String, FacetConfig> getConfigs(FacetCreatorClassifier type) {
		return Optional.ofNullable(collectedConfigs.get(type)).map(cc -> cc.configsByField).orElse(Collections.emptyMap());
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
	private void initNumberFacetCreators(Map<FacetCreatorClassifier, FacetCreator> facetCreatorsByTypes, Map<String, FacetConfig> intervalFacetConfigs, Map<String, FacetConfig> rangeFacetConfigs, String defaultNumberFacetType,
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
				intervalFacetCreator.setGeneralExcludedFields(getIgnoredFieldsOfType(FieldType.NUMBER));
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
				RangeFacetCreator rangeFacetCreator = new RangeFacetCreator(rangeFacetConfigs, null);
				rangeFacetCreator.setExplicitFacetCreator(true);
				rangeFacetCreator.setMaxFacets(maxFacets);
				rangeFacetCreator.setGeneralExcludedFields(getIgnoredFieldsOfType(FieldType.NUMBER));

				if (isVariantLevel) {
					facetCreatorsByTypes.put(FacetCreatorClassifier.variantRangeFacet, new VariantFacetCreator(Collections.singleton(rangeFacetCreator)));
				}
				else {
					facetCreatorsByTypes.put(FacetCreatorClassifier.masterRangeFacet, rangeFacetCreator);
				}
			}
		}

		nonDefaultNumberFacetFields.addAll(getIgnoredFieldsOfType(FieldType.NUMBER));
		defaultNumberFacetCreator.setGeneralExcludedFields(nonDefaultNumberFacetFields);
	}

	private FacetCreator initExplicitFacetCreator(FacetType facetType, Map<String, FacetConfig> explicitConfigs) {
		switch (facetType) {
			case TERM:
				return new TermFacetCreator(explicitConfigs, null, locale, true);
			case INTERVAL:
				return new IntervalFacetCreator(explicitConfigs, null).setExplicitFacetCreator(true);
			case RANGE:
				return new RangeFacetCreator(explicitConfigs, null).setExplicitFacetCreator(true);
			case HIERARCHICAL:
				return new CategoryFacetCreator(explicitConfigs, null, true);
			default:
				log.warn("Not implemented: there is no support for explicit facet creation on type {} for facets ");
				return null;
		}
	}

	private Set<String> getIgnoredFieldsOfType(FieldType fieldType) {
		return Streams.concat(ignoredFields.stream(), customFacetFields.stream(), explicitFacetFields.stream())
				.filter(f -> fieldType.equals(f.getType()))
				.map(Field::getName)
				.collect(Collectors.toSet());
	}

}
