package de.cxp.ocs.smartsuggest.spi;

import java.util.Locale;

import lombok.Data;

@Data
public class SuggestConfig {

	Locale	locale;
	boolean	alwaysDoFuzzy				= Boolean.getBoolean("alwaysDoFuzzy");
	boolean	doReorderSecondaryMatches	= Boolean.getBoolean("doReorderSecondaryMatches");
}
