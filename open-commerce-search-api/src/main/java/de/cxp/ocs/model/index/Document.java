package de.cxp.ocs.model.index;

import java.util.*;

import io.swagger.v3.oas.annotations.media.ArraySchema;
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
					+ " Only values of the following types are accepted (others will be dropped silently):"
					+ " Standard primitive types (Boolean, String, Integer, Double) and arrays of these types."
					+ " Attributes (key-value objects with ID) should be passed to the attributes property.",
			anyOf = {
					Boolean.class,
					Integer.class,
					Long.class,
					Double.class,
					String.class,
					// latest version of swagger-core cannot handle this without NPE
				 	//  Boolean[].class,
					//  Integer[].class,
					//  Long[].class,
					//  Double[].class,
					//  String[].class,
			})
	public Map<String, Object> data = new HashMap<>();

	@Schema(description = "multiple attributes can be delivered separately from standard data fields")
	public List<Attribute> attributes;

	@ArraySchema(
			schema = @Schema(
			description = "categories are treated in a parent-child relationship,"
					+ " so a product can be placed into a path within a category tree."
							+ " Multiple category paths can be defined per document."))
	public List<Category[]> categories;

	public Document() {}

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

	public Document addCategory(Category... values) {
		if (categories == null) categories = new ArrayList<>();
		categories.add(values);
		return this;
	}

}
