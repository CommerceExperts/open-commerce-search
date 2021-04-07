package de.cxp.ocs.preprocessor;

import static de.cxp.ocs.util.Util.deduplicateAdjoinedTokens;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;

import de.cxp.ocs.config.Field;
import de.cxp.ocs.config.FieldType;
import de.cxp.ocs.indexer.model.DataItem;
import de.cxp.ocs.model.index.Document;
import de.cxp.ocs.model.index.Product;

/**
 * Builder class which builds so called combi fields, which are fields whose
 * value is composed of the values of multiple other fields. An example would
 * be:
 * 
 * <pre>
 * searchable_combi where ease the source fields are: title, brand, category_leaf
 * </pre>
 * 
 * @deprecated fields with multiple source field names are treated in the same
 *             way + they also handle attributes. However this preprocessor
 *             joins the fields to one string and cares about removing duplicate
 *             adjoined tokens.
 *             TODO: Add post-processor architecture for IndexableItems where
 *             for example searchable fields can be improved in the same way.
 */
@Deprecated
public class CombiFieldBuilder {

	private static final String	WHITESPACE			= " ";
	private static final String	WHITESPACE_REGEX	= "\\s+";

	private final List<Field> combiFields;

	public CombiFieldBuilder(Map<String, Field> fieldConf) {
		combiFields = fieldConf.values().stream()
				.filter(f -> FieldType.COMBI.equals(f.getType()))
				.collect(Collectors.toList());
	}

	/**
	 * Builds the combi field and writes it's value into the
	 * {@link DataItem}.
	 * 
	 * @param targetItem
	 *        the item to index.
	 */
	public void build(Document targetItem) {
		if (targetItem instanceof Product) {
			generateCombiFields(targetItem, Field::isMasterLevel);
			if (((Product) targetItem).getVariants() != null) {
				for (Document variant : ((Product) targetItem).getVariants()) {
					generateCombiFields(variant, Field::isVariantLevel);
				}
			}
		}
	}

	private void generateCombiFields(Document document, Predicate<Field> combiFieldPredicate) {
		combiFields.forEach(field -> {
			if (combiFieldPredicate.test(field)) {
				String joinedValue = joinCombiFieldValues(document.getData(), field);
				List<String> deduplicateTokens = deduplicateAdjoinedTokens(joinedValue.trim().split(WHITESPACE_REGEX));
				document.set(field.getName(), StringUtils.join(deduplicateTokens, WHITESPACE));
			}
		});
	}

	private String joinCombiFieldValues(Map<String, Object> sourceData, Field field) {
		StringBuilder sb = new StringBuilder();
		for (String sourceName : field.getSourceNames()) {
			if (sb.length() > 0) {
				sb.append(WHITESPACE);
			}
			Object value = sourceData.get(sourceName);
			if (value != null) {
				if (value instanceof Collection<?>) {
					value = ((Collection<?>) value).stream().map(Object::toString).collect(Collectors.joining(
							WHITESPACE));
				}
				sb.append(value);
			}
		}
		return sb.toString();
	}
}
