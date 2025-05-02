package de.cxp.ocs.elasticsearch.query.analyzer;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import org.opentest4j.TestAbortedException;

public class QuerqyQueryExpanderBuilder {

	final List<File> createdTempFiles = new ArrayList<>();

	enum RuleLoadingFlags {
		ASCIIFY, LOWERCASE
	}

	public QuerqyQueryExpander loadWithRules(String... instructions) {
		return this.loadWithRules(EnumSet.noneOf(RuleLoadingFlags.class), instructions);
	}

	public QuerqyQueryExpander loadWithRules(EnumSet<RuleLoadingFlags> loadingFlags, String... instructions) {
		QuerqyQueryExpander underTest = new QuerqyQueryExpander();
		Path querqyRulesFile;
		try {
			querqyRulesFile = Files.createTempFile("querqy_rules_test.", ".txt");
			Files.write(querqyRulesFile, Arrays.asList(instructions), StandardCharsets.UTF_8);
			createdTempFiles.add(querqyRulesFile.toFile());

			Map<String, String> options = new HashMap<>();
			options.put(QuerqyQueryExpander.RULES_URL_PROPERTY_NAME, querqyRulesFile.toAbsolutePath().toString());
			options.put(QuerqyQueryExpander.DO_ASCIIFY_RULES_PROPERTY_NAME, Boolean.toString(loadingFlags.contains(RuleLoadingFlags.ASCIIFY)));
			options.put(QuerqyQueryExpander.DO_LOWERCASE_RULES_PROPERTY_NAME, Boolean.toString(loadingFlags.contains(RuleLoadingFlags.LOWERCASE)));
			underTest.initialize(options);
		}
		catch (IOException e) {
			throw new TestAbortedException("could not write querqy rules file", e);
		}
		return underTest;
	}
}
