package de.cxp.ocs.elasticsearch.query;

import de.cxp.ocs.elasticsearch.model.query.AnalyzedQuery;
import de.cxp.ocs.elasticsearch.model.term.QueryStringTerm;
import de.cxp.ocs.elasticsearch.model.term.WeightedTerm;
import de.cxp.ocs.elasticsearch.model.visitor.QueryTermVisitor;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

@Accessors(chain = true)
@RequiredArgsConstructor
public class QueryStringBuilder implements QueryTermVisitor {

	private final String	termJoinToken;
	private final String	queryJoinToken;

	private final StringBuilder builder = new StringBuilder();

	/**
	 * If enabled, all root-level weighted terms are made fuzzy. All other kind of terms stay untouched.
	 */
	@Setter
	private boolean addFuzzyMarker = false;

	private boolean _lastTermIsBoundaryToken = false;

	public QueryStringBuilder() {
		this(" ", " OR ");
	}

	@Override
	public void visitTerm(QueryStringTerm term) {
		if (builder.length() > 0 && !_lastTermIsBoundaryToken) {
			builder.append(termJoinToken);
			_lastTermIsBoundaryToken = true;
		}

		boolean isAppended = false;
		if (addFuzzyMarker && term instanceof WeightedTerm) {
			WeightedTerm weightedTerm = (WeightedTerm) term;
			if (!weightedTerm.isFuzzy() && !weightedTerm.isQuoted()) {
				builder.append(term.toQueryString()).append("~");
				isAppended = true;
				_lastTermIsBoundaryToken = false;
			}
		}

		if (!isAppended) {
			builder.append(term.toQueryString());
			_lastTermIsBoundaryToken = false;
		}
	}

	@Override
	public void visitSubQuery(AnalyzedQuery query) {
		if (builder.length() > 0 && !_lastTermIsBoundaryToken) {
			builder.append(queryJoinToken);
		}
		builder.append("(");
		_lastTermIsBoundaryToken = true;
		query.accept(this);
		builder.append(")");
	}

	public String getQueryString() {
		return builder.toString();
	}
}
