package de.cxp.ocs.conf;

import de.cxp.ocs.config.DataProcessorConfiguration;
import de.cxp.ocs.config.Field;
import de.cxp.ocs.config.FieldConfiguration;
import de.cxp.ocs.config.IndexSettings;
import lombok.RequiredArgsConstructor;

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
        final FieldConfiguration fieldConfiguration = indexConfig.getFieldConfiguration();
        final FieldConfiguration defaultFieldConfiguration = defaultIndexConfig.getFieldConfiguration();
        final boolean haveFields = !fieldConfiguration.getFields().isEmpty() || !fieldConfiguration.getDynamicFields().isEmpty();
        final boolean useDefaultConfig = fieldConfiguration.isUseDefaultConfig();

        if(!useDefaultConfig && haveFields) {
            return fieldConfiguration;
        } else if (useDefaultConfig && !haveFields) {
            return defaultFieldConfiguration;
        } else if((useDefaultConfig && haveFields)) {
            updatedFieldConfig.getFields().putAll(mergeFields(fieldConfiguration, defaultFieldConfiguration));
            updatedFieldConfig.getDynamicFields().addAll(mergeDynamicFields(fieldConfiguration, defaultFieldConfiguration));
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
        if (Objects.isNull(dynamicFields) || dynamicFields.isEmpty()) {
            return defaultDynamicFields;
        }
        return Stream.concat(dynamicFields.stream(), defaultDynamicFields.stream()).distinct().collect(Collectors.toList());
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
