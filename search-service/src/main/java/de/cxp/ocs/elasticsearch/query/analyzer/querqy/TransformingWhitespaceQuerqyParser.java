package de.cxp.ocs.elasticsearch.query.analyzer.querqy;

import java.util.EnumSet;

import de.cxp.ocs.util.StringUtils;
import querqy.model.Query;
import querqy.parser.QuerqyParser;

public class TransformingWhitespaceQuerqyParser implements QuerqyParser {

	public enum TransformationFlags {
		ASCIIFY, LOWERCASE
	}
	
	private final EnumSet<TransformationFlags>	transformations;
	private final QuerqyParser					delegate;

	public TransformingWhitespaceQuerqyParser(EnumSet<TransformationFlags> transformations, QuerqyParser delegate) {
		this.transformations = EnumSet.copyOf(transformations);
		this.delegate = delegate;
	}

	@Override
	public Query parse(String input) {
		String transformedInput = input;
		if (transformations.contains(TransformationFlags.ASCIIFY)) {
			transformedInput = StringUtils.asciify(transformedInput);
		}
		if (transformations.contains(TransformationFlags.LOWERCASE)) {
			transformedInput = transformedInput.toLowerCase();
		}
		return delegate.parse(transformedInput);
	}

}
