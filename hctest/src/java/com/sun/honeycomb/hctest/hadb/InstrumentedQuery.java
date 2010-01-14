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



package com.sun.honeycomb.hctest.hadb;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.SQLException;

public class InstrumentedQuery {

	public int getFetchSize() {
		return m_fetchSize;
	}

	public void setFetchSize(int fs) {
		m_fetchSize = fs;
	}

	public long getExecTime() {
		return m_execTime;
	}

	public void setConnection(Connection c) throws SQLException {
		m_connection = c;
		m_statement = m_connection.createStatement();
	}

	public String getQryString() {
		return m_qryString;
	}

	public void setQryString(String q) {
		m_qryString = q;
	}

	public ResultSet executeQry(String qry) throws SQLException {
		setQryString(qry);
		return (executeQry());
	}

	public long getRecords() {
		return m_resultSize;
	}

	public ResultSet executeQry() throws SQLException {
		m_execTime = 0;
		long start = System.currentTimeMillis();
		ResultSet res = m_statement.executeQuery(m_qryString);
		m_execTime = System.currentTimeMillis() - start;
		res.setFetchSize(m_fetchSize);
		while (res.next()) {
			m_resultSize++;
		}
		m_statement.close();
		return res;
	}
	
	public Object executeStatement () throws SQLException {
		Object result;
		m_execTime = 0;
		long start = System.currentTimeMillis();
		result = m_statement.executeQuery(m_qryString);
		m_execTime = System.currentTimeMillis() - start;
		return result;
	}
	
	private Connection m_connection;

	private int m_fetchSize;

	private long m_execTime;

	private String m_qryString;

	private Statement m_statement;

	private long m_resultSize;
}
