package com.blazemeter.jmeter.citrix.clause.strategy.check;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.blazemeter.jmeter.citrix.clause.CheckResult;
import com.blazemeter.jmeter.citrix.clause.Clause;
import com.blazemeter.jmeter.citrix.clause.ClauseComputationException;
import com.blazemeter.jmeter.citrix.clause.ClauseHelper;
import com.blazemeter.jmeter.citrix.client.CitrixClient;
import com.blazemeter.jmeter.citrix.client.CitrixClientException;
import com.blazemeter.jmeter.citrix.client.CitrixClient.Snapshot;

/**
 * Provides a clause check stragegy based on regular polling of Citrix client
 * state
 */
public class PollingStrategy implements CheckStrategy {

	private static final Logger LOGGER = LoggerFactory.getLogger(PollingStrategy.class);

	private final ClientChecker checker;
	private final boolean usingExpectedValue;

	@Override
	public boolean isSupportingImageAssessment() {
		return checker instanceof ScreenshotAssessor;
	}

	@Override
	public boolean isSupportingSnapshot() {
		return checker instanceof SnapshotChecker;
	}

	@Override
	public boolean isUsingExpectedValue() {
		return usingExpectedValue;
	}

	/**
	 * Instantiates a new {@link PollingStrategy}
	 * 
	 * @param usingExpectedValue indicates whether the clause expected value is used
	 *                           to define is the check succeeds
	 * @param checker            the method used to check clause using Citrix client
	 *                           state
	 */
	public PollingStrategy(boolean usingExpectedValue, ClientChecker checker) {
		if (checker == null) {
			throw new IllegalArgumentException("checker must not be null");
		}
		this.checker = checker;
		this.usingExpectedValue = usingExpectedValue;
	}

	// Provides a polling context based on the specified clause
	private PollingContext createPollingContext(Clause clause) {
		// NOTE : the valuePredicate will be null when usingExpectedValue is false
		return new PollingContext(usingExpectedValue ? ClauseHelper.getValuePredicate(clause) : null,
				r -> ClauseHelper.getAbsoluteSelection(clause.getSelection(), clause.isRelative(), r));
	}

	@Override
	public boolean wait(Clause clause, CitrixClient client, CheckResultCallback onCheck) throws InterruptedException {

		if (clause == null) {
			throw new IllegalArgumentException("clause must not be null.");
		}

		if (client == null) {
			throw new IllegalArgumentException("client must not be null.");
		}

		// Gets the polling context used for each check
		PollingContext context = createPollingContext(clause);

		// Do checks within the time allotted
		boolean success = false;
		final long maxTime = System.currentTimeMillis() + clause.getTimeout();
		int index = 1;
		while (!success && System.currentTimeMillis() <= maxTime) {

			// Check clause
			CheckResult result = null;
			try {
				result = checker.check(client, context);
				success = result.isSuccessful();
			} catch (CitrixClientException | ClauseComputationException e) {
				LOGGER.info("Unable to compute clause value at test #{}: {}", index, e.getMessage());
				LOGGER.debug("Test #{} fail context:", index, e);
			}

			// Callback function call
			if (onCheck != null) {
				onCheck.apply(result, context.getPrevious(), index);
			}

			if (result != null) {
				context.setPrevious(result);
			}

			if (!success) {
				// Wait until the next check
				TimeUnit.MILLISECONDS.sleep(ClauseHelper.CLAUSE_INTERVAL);
			}
			index++;
		}
		return success;
	}

	@Override
	public CheckResult checkSnapshot(Clause clause, Snapshot snapshot) throws ClauseComputationException {
		if (!isSupportingSnapshot()) {
			throw new UnsupportedOperationException("This strategy does not support snapshot check.");
		}
		return ((SnapshotChecker) checker).check(snapshot, createPollingContext(clause));
	}

	@Override
	public String assess(BufferedImage image, Rectangle selection) throws ClauseComputationException {
		if (!isSupportingImageAssessment()) {
			throw new UnsupportedOperationException("This strategy does not support image assessment.");
		}
		return ((ScreenshotAssessor) checker).assess(image, selection);
	}
}