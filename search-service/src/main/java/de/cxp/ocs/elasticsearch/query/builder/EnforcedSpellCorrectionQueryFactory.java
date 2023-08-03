package de.cxp.ocs.elasticsearch.query.builder;

import de.cxp.ocs.spi.search.ESQueryFactory;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Delegate;

@RequiredArgsConstructor
public class EnforcedSpellCorrectionQueryFactory implements ESQueryFactory {

	@Delegate
	@NonNull
	private final ESQueryFactory delegate;
	
	@Override
	public boolean allowParallelSpellcheckExecution() {
		return true;
	}
}
