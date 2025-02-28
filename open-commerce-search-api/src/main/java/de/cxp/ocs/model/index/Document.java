package de.cxp.ocs.model.index;

import java.util.*;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;

/**
 * A simple 'data record' similar to a row inside a CSV file that contains any
 * data relevant for search. The single field types and conversions are part of
 * the according service configuration.
 */
@Schema(
		name = "Document",
		description = "A data record that contains any data relevant for search."
				+ " The single field types and conversions are part of the according service configuration.",
		requiredProperties = { "id", "data" },
		subTypes = { Product.class })
@Data
@Accessors(chain = true)
@RequiredArgsConstructor
public class Document {

	@NonNull
	public String id;

	@Schema(
			description = "The data property should be used for standard fields, such as title, description, price."
					+ " Values of the following types are accepted (others will be dropped silently):"
					+ " Standard primitive types (Boolean, String, Integer, Double) and arrays of these types."
					+ " Additional hierarchical paths (e.g. categories, taxonomy, etc) can inserted as Category[] (single path)"
					+ " or Category[][] (multiple paths)."
					+ " Attributes (key-value objects with ID) should be passed to the attributes property.",
			anyOf = {
					Boolean.class,
					Integer.class,
					Long.class,
					Double.class,
					String.class,
					Category.class,
					Boolean[].class,
					Integer[].class,
					Long[].class,
					Double[].class,
					String[].class,
					Category[].class,
					Category[][].class,
			})
	public Map<String, Object> data = new HashMap<>();

	@Schema(description = "multiple attributes can be delivered separately from standard data fields")
	public List<Attribute> attributes;

	@Deprecated
	@Schema(
			description = "A category path is a list of Category objects that are defined in a hierarchical parent-child relationship."
					+ "Multiple category paths can be defined per document, therefor this property is a list of category arrays."
					+ "Deprecation Notice: Use data with a specific field name (e.g. 'categories') to put paths as Category[][].",
			contains = Category.class,
			contentSchema = Category.class,
			deprecated = true,
			example = "[[{\"id\":\"7001\",\"name\":\"Electronics\"}, {\"id\":\"7011\",\"name\":\"Notebooks\"}], [{\"id\":\"9000\",\"name\":\"Sale\"}]]")
	public List<Category[]> categories;

	public Document() {
	}

	public Document set(String name, String... values) {
		data.put(name, values.length == 1 ? values[0] : values);
		return this;
	}

	public Document set(String name, long... values) {
		data.put(name, values.length == 1 ? values[0] : values);
		return this;
	}

	public Document set(String name, int... values) {
		data.put(name, values.length == 1 ? values[0] : values);
		return this;
	}

	public Document set(String name, double... values) {
		data.put(name, values.length == 1 ? values[0] : values);
		return this;
	}

	/**
	 * Add a hierarchical path for a given field name. This is usable in case you have multiple different category trees.
	 *
	 * @param name field name
	 * @param values single path hierarchy
	 * @return the modified document (fluent return)
	 */
	public Document addPath(String name, Category... values) {
		Object previousValue = data.get(name);
		if (previousValue == null) {
			data.put(name, values);
		}
		else if (previousValue instanceof Category[]) {
			Category[][] newPaths = new Category[2][];
			newPaths[0] = (Category[]) previousValue;
			newPaths[1] = values;
			data.put(name, newPaths);
		}
		else if (previousValue instanceof Category[][] prevPaths) {
			Category[][] newPaths = new Category[prevPaths.length + 1][];
			int i = 0;
			for (Category[] path : prevPaths) {
				newPaths[i++] = path;
			}
			newPaths[i] = values;
			data.put(name, newPaths);
		}
		return this;
	}

	public Document setAttributes(Attribute... values) {
		attributes = Arrays.asList(values);
		return this;
	}

	public Document addAttribute(Attribute attr) {
		if (attributes == null) {
			attributes = new ArrayList<>();
		}
		else if (!(attributes instanceof ArrayList)) {
			// make mutable
			attributes = new ArrayList<>(attributes);
		}
		attributes.add(attr);
		return this;
	}

	/**
	 * @deprecated use addPath with a specific name. For legacy usage, use the field name used in the according indexer field configuration.
	 * @param values single path
	 * @return modified document (fluent return)
	 */
	@Deprecated
	public Document addCategory(Category... values) {
		if (categories == null) categories = new ArrayList<>();
		categories.add(values);
		return this;
	}

}
