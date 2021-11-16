package de.cxp.ocs.smartsuggest.spi.standard;

import java.util.Locale;

import de.cxp.ocs.smartsuggest.spi.SuggestConfig;
import de.cxp.ocs.smartsuggest.spi.SuggestConfigProvider;
import lombok.NonNull;

public class DefaultSuggestConfigProvider implements SuggestConfigProvider {

	@Override
	public SuggestConfig get(@NonNull String indexName) {
		SuggestConfig _default = new SuggestConfig();
		_default.setLocale(Locale.ROOT);
		_default.setAlwaysDoFuzzy(Boolean.getBoolean("alwaysDoFuzzy"));
		_default.setDoReorderSecondaryMatches(Boolean.getBoolean("doReorderSecondaryMatches"));
		return _default;
	}

}
