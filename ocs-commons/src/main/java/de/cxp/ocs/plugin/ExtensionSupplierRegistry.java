package de.cxp.ocs.plugin;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ExtensionSupplierRegistry<E> {

	@Getter
	private Map<String, Supplier<? extends E>> extensionSuppliers = new HashMap<>();

	@SuppressWarnings("unchecked")
	public <T extends E> void register(T instance) {
		Class<T> clazz = (Class<T>) instance.getClass();
		register(clazz, () -> {
			try {
				return clazz.newInstance();
			}
			catch (InstantiationException | IllegalAccessException e) {
				throw new IllegalStateException("Custom class " + clazz + " misses a required default construtor", e);
			}
		});
	}

	public <T extends E> void register(Class<T> clazz, Supplier<T> supplier) {
		if (extensionSuppliers.put(clazz.getSimpleName(), supplier) != null) {
			log.warn("the simple class name {} from the class {} was already registered and overwritten!",
					clazz.getSimpleName(), clazz.getCanonicalName());
		}
		if (extensionSuppliers.put(clazz.getCanonicalName(), supplier) != null) {
			log.warn("Extension class {} was already registered and overwritten!",
					clazz.getCanonicalName());
		}
	}
}
