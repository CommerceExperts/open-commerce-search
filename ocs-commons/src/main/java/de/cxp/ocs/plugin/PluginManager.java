package de.cxp.ocs.plugin;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
	private final Map<String, String> preferredServies;

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

	public <T> Optional<T> loadPrefered(Class<T> serviceInterface) {
		T preferedService = null;
		String preferredServiceClass = preferredServies.get(serviceInterface.getCanonicalName());

		if (preferredServiceClass != null) {
			try {
				// try with class loader. Since there can be some magic class
				// loader issues, this is just optional - below we try to
				// find that class as well using the serviceLoader
				Class<?> loadedServiceImpl = this.getClass().getClassLoader().loadClass(preferredServiceClass);
			}
			catch (Exception e) {
				log.warn("could not load prefered implementation {} for service {}", preferredServiceClass, serviceInterface.getCanonicalName(), e);
			}
		}

		// no luck with the class loader or just no preferred class configured
		if (preferedService == null) {
			ServiceLoader<T> loader = ServiceLoader.load(serviceInterface);
			Iterator<T> serviceImpls = loader.iterator();
			List<T> loadedServices = new ArrayList<>();
			while (serviceImpls.hasNext()) {
				T next = serviceImpls.next();
				if (disabledServies.contains(next.getClass().getCanonicalName())) {
					log.info("Service {} for interface {} is disabled", next.getClass(), serviceInterface.getClass());
				}
				// take first if non is prefered
				if (preferedService == null) preferedService = next;
				if (preferredServiceClass == null) break;

				// If we accidently find the preferred (but could not be loaded
				// before because of some classloader magic, then
				if (next.getClass().getCanonicalName().equals(preferredServiceClass)) {
					preferedService = next;
					break;
				}
			}
		}
		return Optional.ofNullable(preferedService);
	}
}
