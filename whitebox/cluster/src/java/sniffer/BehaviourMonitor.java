/*
 * Copyright © 2008, Sun Microsystems, Inc.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *
 *    * Redistributions of source code must retain the above copyright
 * notice, this list of conditions and the following disclaimer.
 *
 *    * Redistributions in binary form must reproduce the above copyright
 * notice, this list of conditions and the following disclaimer in the
 * documentation and/or other materials provided with the distribution.
 *
 *    * Neither the name of Sun Microsystems, Inc. nor the names of its
 * contributors may be used to endorse or promote products derived from
 * this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
 * IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED
 * TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A
 * PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER
 * OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */



package sniffer;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.logging.Logger;

import sun.misc.Signal;
import sun.misc.SignalHandler;
import util.Constants;

public class BehaviourMonitor implements SignalHandler {

	private static final String CONFIG_FILE = "/config/sniffer_behaviour.conf";

	public static String DROP_ACTION = "DROP";
	public static String DELAY_ACTION = "DELAY";
	public static String NOTHING_ACTION = "NOTHING";

	public static String REPEAT_RULE_TYPE = "REPEAT";
	public static String ONCE_RULE_TYPE = "ONCE";

	private ArrayList behaviourRules = new ArrayList();

	private static BehaviourMonitor singleton = null;

	private static final Logger LOG = Logger.getLogger(BehaviourMonitor.class
			.getName());

	private BehaviourMonitor() {
		super();
		install("USR1");
	}

	public static synchronized BehaviourMonitor getInstance()
			throws IOException {
		if (singleton == null) {
			singleton = new BehaviourMonitor();
			singleton.init();
		}
		return (singleton);
	}

	public SignalHandler oldHandler;

	// Static method to install the signal handler
	public SignalHandler install(String signalName) {
		Signal diagSignal = new Signal(signalName);
		SignalHandler diagHandler = this;
		oldHandler = Signal.handle(diagSignal, diagHandler);
		return diagHandler;
	}

	public void handle(Signal sig) {
		try {
			LOG.info("Reading config file.");
			// Read sniffer config file and update sniffer behaviour...
			init();

			// Chain back to previous handler, if one exists
			if (oldHandler != SIG_DFL && oldHandler != SIG_IGN) {
				oldHandler.handle(sig);
			}

		} catch (Exception e) {
			LOG.severe("Signal handler failed, reason " + e);
		}
	}

	private void init() throws IOException {
		try {
			FileInputStream fis = new FileInputStream(new File(Constants
					.getRootDir()
					+ CONFIG_FILE));

			byte[] buffer = new byte[1024];
			StringBuffer newBehaviour = new StringBuffer();

			while (fis.available() > 0) {
				fis.read(buffer);
				newBehaviour.append(new String(buffer));
			}

			fis.close();

			parseBehaviourString(newBehaviour.toString());
		} catch (Exception e) {
			throw new IOException("initBehaviour error " + e.getMessage());
		}
	}

	private synchronized void parseBehaviourString(String behaviour) {
		String[] rules = behaviour.split("\n");

		behaviourRules.clear();
		rule_index = 0;

		for (int i = 0; i < rules.length; i++) {
			// For each line in the behaviour string there should be:
			// TIME:ACTION:PACKET_TYPE:ARGS:RULE_TYPE

			// Ignore blank lines.
			if (rules[i].replaceAll("\n", "").trim().length() != 0) {
				String[] elements = rules[i].trim().split(":");
				if (elements.length != 5)
					throw new IllegalArgumentException(
							"behaviour rule is not valid: " + behaviour);
				else {
					// Parse Rule
					Rule rule = new Rule();

					try {
						rule.time = new Long(elements[0]).longValue();
					} catch (NumberFormatException e) {
						throw new IllegalArgumentException(
								"time field in rule:" + rules[i]
										+ "is not a number.", e);
					}

					rule.action = elements[1];
					rule.packet_type = elements[2];
					rule.args = elements[3];
					rule.rule_type = elements[4];

					// Some rule validation...
					if (!rule.action.matches(DROP_ACTION + "|" + DELAY_ACTION
							+ "|" + NOTHING_ACTION))
						throw new IllegalArgumentException(
								"action field in rule: " + rules[i]
										+ " must be one of the following: "
										+ DROP_ACTION + "," + DELAY_ACTION
										+ "," + NOTHING_ACTION);

					if (!rule.rule_type.matches(REPEAT_RULE_TYPE + "|"
							+ ONCE_RULE_TYPE))
						throw new IllegalArgumentException(
								"rule_type field in rule: " + rules[i]
										+ " must be one of the following: "
										+ REPEAT_RULE_TYPE + ","
										+ ONCE_RULE_TYPE);

					behaviourRules.add(behaviourRules.size(), rule);
				}
			}
		}
	}

	private long starttime = System.currentTimeMillis();
	private int rule_index = 0;

	public synchronized Rule getNextRule() {
		if (behaviourRules.size() == 0)
			return new Rule();
		else {
			Rule current = (Rule) behaviourRules.get(rule_index);

			if (System.currentTimeMillis() - starttime > current.time * 1000) {
				// Next Rule...
				rule_index++;

				if (rule_index >= behaviourRules.size())
					rule_index = 0;

				starttime = System.currentTimeMillis();
				current = (Rule) behaviourRules.get(rule_index);
				
				if (current.rule_type == ONCE_RULE_TYPE) {
					behaviourRules.remove(rule_index);
				}
			}

			return current;
		}
	}
}
