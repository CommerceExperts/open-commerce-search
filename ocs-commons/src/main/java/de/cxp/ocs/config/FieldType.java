package de.cxp.ocs.config;

public enum FieldType {
	// FIXME: "id" and "combi" are no real types. Use other ways to handle that
	// use case. (FieldUsage.id and direct configuration for
	// CombiFieldPreprocessor)
	string, number, category, id, combi;
}
