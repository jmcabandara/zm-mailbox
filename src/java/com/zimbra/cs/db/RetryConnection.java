/*
 * ***** BEGIN LICENSE BLOCK *****
 * Zimbra Collaboration Suite Server
 * Copyright (C) 2010, 2013 Zimbra, Inc.
 * 
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software Foundation,
 * version 2 of the License.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License along with this program.
 * If not, see <http://www.gnu.org/licenses/>.
 * ***** END LICENSE BLOCK *****
 */
package com.zimbra.cs.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * Connection wrapper that uses RetryPreparedStatement
 *
 */
public class RetryConnection extends DebugConnection {

    RetryConnection(Connection conn) {
        super(conn);
    }

    @Override
    public PreparedStatement prepareStatement(final String sql) throws SQLException {
        final RetryConnection conn = this;
        AbstractRetry<PreparedStatement> exec = new AbstractRetry<PreparedStatement>() {
            @Override
            public ExecuteResult<PreparedStatement> execute() throws SQLException {
                return new ExecuteResult<PreparedStatement>(
                        new RetryPreparedStatement(conn, mConn.prepareStatement(sql), sql));
            }
        };
        return exec.doRetry().getResult();
    }


    @Override
    public PreparedStatement prepareStatement(final String sql, final int resultSetType,
                                              final int resultSetConcurrency)
    throws SQLException {
        final RetryConnection conn = this;
        AbstractRetry<PreparedStatement> exec = new AbstractRetry<PreparedStatement>() {
            @Override
            public ExecuteResult<PreparedStatement> execute() throws SQLException {
                return new ExecuteResult<PreparedStatement>(
                        new RetryPreparedStatement(conn, mConn.prepareStatement(
                                sql, resultSetType, resultSetConcurrency), sql));
            }
        };
        return exec.doRetry().getResult();
    }

    @Override
    public PreparedStatement prepareStatement(final String sql, final int resultSetType,
                                              final int resultSetConcurrency,
                                              final int resultSetHoldability)
    throws SQLException {
        final RetryConnection conn = this;
        AbstractRetry<PreparedStatement> exec = new AbstractRetry<PreparedStatement>() {
            @Override
            public ExecuteResult<PreparedStatement> execute() throws SQLException {
                return new ExecuteResult<PreparedStatement>(new RetryPreparedStatement(
                        conn, mConn.prepareStatement(sql, resultSetType,
                                resultSetConcurrency, resultSetHoldability), sql));
            }
        };
        return exec.doRetry().getResult();
    }

    @Override
    public PreparedStatement prepareStatement(final String sql, final int autoGeneratedKeys)
    throws SQLException {
        final RetryConnection conn = this;
        AbstractRetry<PreparedStatement> exec = new AbstractRetry<PreparedStatement>() {
            @Override
            public ExecuteResult<PreparedStatement> execute() throws SQLException {
                return new ExecuteResult<PreparedStatement>(new RetryPreparedStatement(
                        conn, mConn.prepareStatement(sql, autoGeneratedKeys), sql));
            }
        };
        return exec.doRetry().getResult();
    }

    @Override
    public PreparedStatement prepareStatement(final String sql, final int[] columnIndexes)
    throws SQLException {
        final RetryConnection conn = this;
        AbstractRetry<PreparedStatement> exec = new AbstractRetry<PreparedStatement>() {
            @Override
            public ExecuteResult<PreparedStatement> execute() throws SQLException {
                return new ExecuteResult<PreparedStatement>(new RetryPreparedStatement(
                        conn, mConn.prepareStatement(sql, columnIndexes), sql));
            }
        };
        return exec.doRetry().getResult();
    }

    @Override
    public PreparedStatement prepareStatement(final String sql, final String[] columnNames)
        throws SQLException {
        final RetryConnection conn = this;
        AbstractRetry<PreparedStatement> exec = new AbstractRetry<PreparedStatement>() {
            @Override
            public ExecuteResult<PreparedStatement> execute() throws SQLException {
                return new ExecuteResult<PreparedStatement>(new RetryPreparedStatement(
                        conn, mConn.prepareStatement(sql, columnNames), sql));
            }
        };
        return exec.doRetry().getResult();
    }

    @Override
    @SuppressWarnings("unchecked")
    public void commit() throws SQLException {
        AbstractRetry exec = new AbstractRetry () {
            @Override
            public ExecuteResult execute() throws SQLException {
                superCommit();
                return null;
            }
        };
        exec.doRetry();
    }
    
    private void superCommit() throws SQLException {
        super.commit();
    }

    @Override
    @SuppressWarnings("unchecked")
    public void rollback() throws SQLException {
        AbstractRetry exec = new AbstractRetry () {
            @Override
            public ExecuteResult execute() throws SQLException {
                superRollback();
                return null;
            }
        };
        exec.doRetry();
    }
    
    private void superRollback() throws SQLException {
        super.rollback();
    }
}
