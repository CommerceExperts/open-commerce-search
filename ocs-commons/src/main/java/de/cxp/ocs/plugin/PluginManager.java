package de.cxp.ocs.plugin;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class PluginManager {

	/**
	 * Set of canonical names that should be ignored completely.
	 */
	@NonNull
	private final Set<String> disabledServies;

	/**
	 * Canonical SPI names as key and canonical implementation names as value.
	 * Is used if a single implementation is required but several are available.
	 */
	@NonNull
	private final Map<String, String> preferedServies;

	public <T> List<T> loadAll(Class<T> serviceInterface) {
		ServiceLoader<T> loader = ServiceLoader.load(serviceInterface);
		Iterator<T> serviceImpls = loader.iterator();
		List<T> loadedServices = new ArrayList<>();
		while (serviceImpls.hasNext()) {
			T next = serviceImpls.next();
			if (disabledServies.contains(next.getClass().getCanonicalName())) {
				log.info("Service {} for interface {} is disabled", next.getClass(), serviceInterface.getClass());
			}
			else {
				loadedServices.add(next);
			}
		}
		return loadedServices;
	}
}
