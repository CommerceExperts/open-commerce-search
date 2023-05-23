package de.cxp.ocs.elasticsearch.query.analyzer.querqy;

import java.util.EnumSet;

import de.cxp.ocs.elasticsearch.query.analyzer.querqy.TransformingWhitespaceQuerqyParser.TransformationFlags;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import querqy.parser.QuerqyParser;
import querqy.rewrite.commonrules.QuerqyParserFactory;
import querqy.rewrite.commonrules.WhiteSpaceQuerqyParserFactory;

@RequiredArgsConstructor
public class TransformingWhitespaceQuerqyParserFactory implements QuerqyParserFactory {

	private final QuerqyParserFactory			delegate	= new WhiteSpaceQuerqyParserFactory();

	@NonNull
	private final EnumSet<TransformationFlags>	transformationFlags;

	@Override
	public QuerqyParser createParser() {
		return new TransformingWhitespaceQuerqyParser(transformationFlags, delegate.createParser());
	}

}
