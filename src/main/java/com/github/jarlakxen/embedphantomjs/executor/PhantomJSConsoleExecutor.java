/**
 * Copyright (C) 2013
 *   Facundo Viale <fviale@despegar.com>
 *
 * with contributions from
 * 	Facundo Viale (Jarlakxen@github)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.jarlakxen.embedphantomjs.executor;

import static java.util.Arrays.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.util.List;

import org.apache.commons.io.IOUtils;
import org.apache.log4j.Logger;

import com.github.jarlakxen.embedphantomjs.PhantomJSReference;
import com.github.jarlakxen.embedphantomjs.exception.UnexpectedProcessEndException;

public class PhantomJSConsoleExecutor {

	private static final Logger LOGGER = Logger.getLogger(PhantomJSConsoleExecutor.class);

	private static final char SYSTEM_NEWLINE[] = System.getProperty("line.separator").toString().toCharArray();
	private static final String PHANTOMJS_CONSOLE_PREFIX = "phantomjs> ";
	private static final List<String> PHANTOMJS_CONSOLE_POSTFIXS = asList("{}", "undefined");

	private PhantomJSReference phantomReference;
	private Process process;

	public PhantomJSConsoleExecutor(PhantomJSReference phantomReference) {
		this.phantomReference = phantomReference;
	}

	public int getPid() {
		if (process.getClass().getName().equals("java.lang.UNIXProcess")) {
			/* get the PID on unix/linux systems */
			try {
				Field f = process.getClass().getDeclaredField("pid");
				f.setAccessible(true);
				return f.getInt(process);
			} catch (Throwable e) {
			}
		}

		return -1;
	}

	public boolean isAlive() {
		try {
			process.exitValue();
			return false;
		} catch (IllegalThreadStateException ex) {
			return true;
		}
	}

	public void start() {
		try {
			process = Runtime.getRuntime().exec(this.phantomReference.getBinaryPath());
			process.getInputStream().read(new byte[PHANTOMJS_CONSOLE_PREFIX.length()]);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public int destroy() {
		try {
			process.destroy();
		} catch (Exception e) {
		}

		if (isAlive()) {
			try {
				return process.waitFor();
			} catch (InterruptedException e) {

			}
		}

		return process.exitValue();
	}

	public String execute(final String scriptSource) throws UnexpectedProcessEndException {
		return this.execute(IOUtils.toInputStream(scriptSource), PHANTOMJS_CONSOLE_POSTFIXS);
	}

	public String execute(final String scriptSource, String... endLines) throws UnexpectedProcessEndException {
		return this.execute(IOUtils.toInputStream(scriptSource), asList(endLines));
	}

	public String execute(final InputStream scriptSourceInputStream, String... endLines) throws UnexpectedProcessEndException {
		return this.execute(scriptSourceInputStream, asList(endLines));
	}

	public synchronized String execute(final InputStream scriptSourceInputStream, List<String> endLines)
			throws UnexpectedProcessEndException {

		if (!isAlive()) {
			throw new UnexpectedProcessEndException();
		}

		try {
			IOUtils.copy(scriptSourceInputStream, process.getOutputStream());
			// Append Enter to the input

			for (char c : SYSTEM_NEWLINE) {
				process.getOutputStream().write(c);
			}

			process.getOutputStream().flush();

			String output = readPhantomJSOutput(process.getInputStream(), endLines);

			LOGGER.debug("Program output: " + output);

			return output;
		} catch (IOException e) {
			throw new UnexpectedProcessEndException(e);
		}
	}

	private String readPhantomJSOutput(InputStream processInput, List<String> endLines) throws IOException {

		final StringBuilder out = new StringBuilder();

		BufferedReader in = new BufferedReader(new InputStreamReader(processInput, "UTF-8"));

		while (true) {
			String line = in.readLine();

			if (line == null || endLines.contains(line)) {
				in.skip(PHANTOMJS_CONSOLE_PREFIX.length());
				break;
			}

			if (out.length() > 0) {
				out.append("\n");
			}

			out.append(line);
		}

		return out.toString();
	}
}
