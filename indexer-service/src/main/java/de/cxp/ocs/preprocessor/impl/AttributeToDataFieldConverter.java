package de.cxp.ocs.preprocessor.impl;

import java.util.*;

import org.apache.commons.lang3.StringUtils;

import de.cxp.ocs.config.FieldConfigAccess;
import de.cxp.ocs.model.index.Attribute;
import de.cxp.ocs.model.index.Document;
import de.cxp.ocs.spi.indexer.DocumentPreProcessor;
import de.cxp.ocs.util.Util;

/**
 * <p>
 * Converts attributes to a standard data field.
 * Per default all attributes are taken that do not have an ID.
 * </p>
 * <p>
 * If the include option is set, the according attribute is also converted even if
 * it has an ID.
 * </p>
 * <p>
 * As option for the attribute converter, include or exclude can be defined to
 * restrict the attributes that should be converted.
 * <ul>
 * <li>include: name1,name2</li>
 * <li>exclude: name3</li>
 * </ul>
 * If include is defined, only the according attributes are converted. So it does not make sense to defined both options
 * </p>
 * <p>
 * Attention: be aware that attributes also have a special handling regarding dynamic configuration.
 * If an attribute is converted into a document field, the according field-configuration might not apply anymore.
 * </p>
 * 
 * @author rb@commerce-experts.com
 */
public class AttributeToDataFieldConverter implements DocumentPreProcessor {

	private Set<String>	includes;
	private Set<String>	excludes;

	@Override
	public void initialize(FieldConfigAccess fieldConfig, Map<String, String> preProcessorConfig) {
		includes = Optional.ofNullable(preProcessorConfig.get("include")).map(opt -> StringUtils.split(opt, ','))
				.<Set<String>> map(arr -> new HashSet<>(Arrays.asList(arr))).orElse(null);
		excludes = Optional.ofNullable(preProcessorConfig.get("exclude")).map(opt -> StringUtils.split(opt, ','))
				.<Set<String>> map(arr -> new HashSet<>(Arrays.asList(arr))).orElse(null);
	}

	@Override
	public boolean process(Document sourceDocument, boolean visible) {
		if (sourceDocument.attributes == null) return visible;

		List<Attribute> modifiedAttributes = new ArrayList<>(sourceDocument.attributes);
		Iterator<Attribute> attributeIterator = modifiedAttributes.iterator();
		while (attributeIterator.hasNext()) {
			Attribute attr = attributeIterator.next();
			if (attr.value != null && applicableForConversion(attr)) {
				Object value = sourceDocument.getData().get(attr.getName());
				value = Util.collectObjects(value, attr.getValue());
				sourceDocument.data.put(attr.getName(), value);
				attributeIterator.remove();
			}
		}
		sourceDocument.attributes = modifiedAttributes;
		return visible;
	}

	private boolean applicableForConversion(Attribute attr) {
		if (includes != null) return includes.contains(attr.getName());
		if (excludes != null) return !excludes.contains(attr.getName()) && attr.getCode() == null;
		else return attr.getCode() == null;
	}

}
