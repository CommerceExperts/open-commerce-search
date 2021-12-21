package de.cxp.ocs.config.logging;

import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.filter.AbstractMatcherFilter;
import ch.qos.logback.core.spi.FilterReply;

/**
 * Non-turbo filter checks whether the marker in the event matches the marker
 * specified by user.
 * 
 * @author Gabriel Bauer
 *
 */
public class MarkerFilter extends AbstractMatcherFilter<ILoggingEvent> {

	protected Marker marker;

	/**
	 * Make a decision based on a logging event passed as an argument.
	 */
	@Override
	public FilterReply decide(ILoggingEvent event) {
		if (!isStarted()) { // this means no marker property specified
			return FilterReply.NEUTRAL;
		}

		final Marker eMarker = event.getMarker();

		if (marker.equals(eMarker) || marker.contains(eMarker)) {
			return onMatch;
		}

		return onMismatch;
	}

	@Override
	public void start() {
		if (null == marker) {
			addError("The marker property must be set for [" + getName() + "].");
			return;
		}
		super.start();
		addInfo("Filter[" + getName() + "] matches the marker [" + marker.getName()
				+ "], returns [" + onMatch + "] on match, [" + onMismatch + "] on mismatch");
	}

	/**
	 * The marker to match in the event.
	 * 
	 * @param markerName
	 *        marker name
	 */
	public void setMarker(String markerName) {
		marker = MarkerFactory.getMarker(markerName);
	}
}
