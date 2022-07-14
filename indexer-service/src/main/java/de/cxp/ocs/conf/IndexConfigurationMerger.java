package de.cxp.ocs.conf;

import de.cxp.ocs.config.DataProcessorConfiguration;
import de.cxp.ocs.config.Field;
import de.cxp.ocs.config.FieldConfiguration;
import de.cxp.ocs.config.IndexSettings;
import lombok.RequiredArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@RequiredArgsConstructor
public class IndexConfigurationMerger {

    private final IndexConfiguration indexConfig;
    private final IndexConfiguration defaultIndexConfig;

    public IndexConfiguration getIndexConfig() {
        final IndexConfiguration indexConfiguration = new IndexConfiguration();
        indexConfiguration.setIndexSettings(getIndexSettings());
        indexConfiguration.setDataProcessorConfiguration(getDataProcessConfiguration());
        indexConfiguration.setFieldConfiguration(getFieldConfiguration());
        return indexConfiguration;
    }

    private FieldConfiguration getFieldConfiguration() {
        final FieldConfiguration updatedFieldConfig = new FieldConfiguration();
        final FieldConfiguration filedConfiguration = indexConfig.getFieldConfiguration();
        final FieldConfiguration defaultFieldConfiguration = defaultIndexConfig.getFieldConfiguration();

        if (Objects.isNull(filedConfiguration) || (filedConfiguration.getFields().isEmpty()
                && filedConfiguration.getDynamicFields().isEmpty()) || filedConfiguration.isUseDefaultConfig()) {
            return defaultFieldConfiguration;
        } else {
            updatedFieldConfig.getFields().putAll(mergeFields(filedConfiguration, defaultFieldConfiguration));
            updatedFieldConfig.getDynamicFields().addAll(mergeDynamicFields(filedConfiguration, defaultFieldConfiguration));
        }
        return updatedFieldConfig;
    }

    /**
     * Merges the Index specific field configuration and default field configuration and overrides the default field configuration
     * @param filedConfiguration index specific field configuration
     * @param defaultFieldConfiguration default field configuration.
     * @return merged map.
     */
    private Map<String, Field> mergeFields(final FieldConfiguration filedConfiguration, final FieldConfiguration defaultFieldConfiguration) {
        return Stream.of(filedConfiguration.getFields(), defaultFieldConfiguration.getFields())
                .flatMap(map -> map.entrySet().stream())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (v1, v2) -> v1));
    }

    private List<Field> mergeDynamicFields(final FieldConfiguration fieldConfiguration, final FieldConfiguration defaultFieldConfiguration) {
        final List<Field> dynamicFields = fieldConfiguration.getDynamicFields();
        final List<Field> defaultDynamicFields= defaultFieldConfiguration.getDynamicFields();
        if (Objects.isNull(dynamicFields)) {
            return defaultDynamicFields;
        }
        final List<Field> updatedFields = new ArrayList<>(dynamicFields);
        dynamicFields.forEach(field -> updatedFields.addAll(getUniqueFields(field ,defaultDynamicFields, updatedFields)));
        return updatedFields;
    }

    private List<Field> getUniqueFields(final Field field, final List<Field> defaultDynamicFields, final List<Field> updatedDynamicFields) {
        return defaultDynamicFields.stream().filter(f -> !isSimilarField(f, field) && !updatedDynamicFields.contains(f)).collect(Collectors.toList());
    }

    private boolean isSimilarField(final Field field1, final Field field2) {
       return field1.getName().equalsIgnoreCase(field2.getName()) && field1.getType().equals(field2.getType());
    }

    private DataProcessorConfiguration getDataProcessConfiguration() {
        final DataProcessorConfiguration dpc = indexConfig.getDataProcessorConfiguration();
        if (Objects.isNull(dpc) || dpc.isUseDefaultConfig()) {
            return defaultIndexConfig.getDataProcessorConfiguration();
        }
        return dpc;
    }

    private IndexSettings getIndexSettings() {
        final IndexSettings indexSettings = indexConfig.getIndexSettings();
        if (Objects.isNull(indexSettings) || indexSettings.isUseDefaultConfig()) {
            return defaultIndexConfig.getIndexSettings();
        }
        return indexSettings;
    }
}
