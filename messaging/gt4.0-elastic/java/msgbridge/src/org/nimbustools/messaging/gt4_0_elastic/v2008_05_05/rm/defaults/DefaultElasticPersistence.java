/*
 * Copyright 1999-2011 University of Chicago
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy
 * of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package org.nimbustools.messaging.gt4_0_elastic.v2008_05_05.rm.defaults;

import org.apache.commons.dbcp.BasicDataSource;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.core.io.Resource;

import javax.sql.DataSource;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class DefaultElasticPersistence {

        private static final Log logger =
            LogFactory.getLog(DefaultElasticPersistence.class.getName());

    //schema
    static final String SCHEMA_CREATE_INSTANCES =
            "create table if not exists instances (" +
                    "    elastic_id varchar(10) unique not null," +
                    "    manager_id varchar(10) unique not null," +
                    "    reservation_id varchar(10) not null," +
                    "    sshkey varchar(128))";
    static final String SCHEMA_CREATE_RESERVATIONS =
            "create table if not exists reservations (" +
                    "    reservation_id varchar(10) primary key," +
                    "    group_id varchar(10)," +
                    "    cosched_id varchar(10))";
    static final String SCHEMA_CREATE_VERSION =
            "create table if not exists schema_version (version int primary key)";
    static final String SCHEMA_INSERT_VERSION =
            "insert into schema_version (version) values(0)";
    static final String[] SCHEMA_ALL =
            new String[]{SCHEMA_CREATE_INSTANCES, SCHEMA_CREATE_RESERVATIONS,
                    SCHEMA_CREATE_VERSION, SCHEMA_INSERT_VERSION};
    static final String GET_SCHEMA_VERSION =
            "select version from schema_version limit 1";
    static final String INSERT_INSTANCE = "insert into instances " +
            "(elastic_id, manager_id, reservation_id, sshkey) values(?,?,?,?)";
    static final String GET_MANAGER_FROM_ELASTIC_INSTANCE =
            "select manager_id from instances where elastic_id = ?";
    static final String GET_ELASTIC_FROM_MANAGER_INSTANCE =
            "select elastic_id from instances where manager_id = ?";
    static final String INSERT_RESERVATION = "insert into reservations " +
            "(reservation_id, group_id, cosched_id) values(?,?,?)";
    static final String GET_SSHKEY_FROM_ELASTIC_INSTANCE =
            "select sshkey from instances where elastic_id = ?";
    static final String GET_GROUP_FROM_ELASTIC_RESERVATION =
            "select group_id from reservations where reservation_id = ?";
    static final String GET_COSCHED_FROM_ELASTIC_RESERVATION =
            "select cosched_id from reservations where reservation_id = ?";
    static final String GET_RESERVATION_FROM_MANAGER_INSTANCE =
            "select reservation_id from instances where manager_id = ?";
    static final String GET_RESERVATION_FROM_GROUP =
            "select reservation_id from reservations where group_id = ?";
    static final String GET_RESERVATION_FROM_COSCHED =
            "select reservation_id from reservations where cosched_id = ?";
    // a little silly
    static final String GET_RESERVATION =
            "select reservation_id from reservations where reservation_id = ?";

    private final DataSource dataSource;

    public DefaultElasticPersistence(Resource dbResource) throws IOException {
        if (dbResource == null) {
            throw new IllegalArgumentException("diskStoreResource may not be null");
        }

        final String dbPath = dbResource.getFile().getAbsolutePath();

        //don't know how to feed this in with Spring and still have it fixup the
        // $NIMBUS_HOME in path
        final BasicDataSource ds = new BasicDataSource();
        ds.setDriverClassName("org.sqlite.JDBC");
        ds.setUrl("jdbc:sqlite://"+dbPath);

        this.dataSource = ds;
    }

    public void initialize() throws Exception {
        if (this.dataSource == null) {
            throw new IllegalArgumentException("this.dataSource may not be null");
        }


        // make sure we can get a connection
        final Connection connection = this.dataSource.getConnection();
        returnConnection(connection);

        if (!this.checkSchema()) {
            logger.info("Creating elastic sqlite schema");
            this.createSchema();
        }
    }

    private boolean checkSchema() {
        Connection c = null;
        PreparedStatement pstmt = null;
        ResultSet rs;

        try {
            c = dataSource.getConnection();
            pstmt = c.prepareStatement(DefaultElasticPersistence.GET_SCHEMA_VERSION);
            rs = pstmt.executeQuery();
            // we're just interested in whether there are any rows. in the future this
            // could be used for automatically updating schemas
            return rs.next();
        } catch (SQLException e) {
            logger.debug("Error querying elastic sqlite schema version", e);
            return false;
        } finally {
            try {
                if (pstmt != null) {
                    pstmt.close();
                }
                if (c != null) {
                    returnConnection(c);
                }
            } catch (SQLException sql) {
                logger.error("SQLException in finally cleanup", sql);
            }
        }
    }

    private void createSchema() throws Exception {
        Connection c = null;
        PreparedStatement pstmt = null;

        try {
            c = dataSource.getConnection();

            for (String query : DefaultElasticPersistence.SCHEMA_ALL) {
                pstmt = c.prepareStatement(query);
                pstmt.executeUpdate();
            }

        } catch (SQLException e) {
            logger.error("", e);
            throw e;
        } finally {
            try {
                if (pstmt != null) {
                    pstmt.close();
                }
                if (c != null) {
                    returnConnection(c);
                }
            } catch (SQLException sql) {
                logger.error("SQLException in finally cleanup", sql);
            }
        }

    }

    public void insertInstance(String elasticInstanceId,
                               String managerInstanceId,
                               String elasticReservationId,
                               String sshKeyUsed) throws Exception {
        Connection c = null;
        PreparedStatement pstmt = null;

        try {
            c = dataSource.getConnection();
            pstmt = c.prepareStatement(DefaultElasticPersistence.INSERT_INSTANCE);
            pstmt.setString(1, elasticInstanceId);
            pstmt.setString(2, managerInstanceId);
            pstmt.setString(3, elasticReservationId);
            pstmt.setString(4, sshKeyUsed);

            int rc = pstmt.executeUpdate();
            if (rc != 1) {
                throw new Exception("did not insert the row properly");
            }
        } catch (SQLException e) {
            logger.error("", e);
            throw e;
        } finally {
            try {
                if (pstmt != null) {
                    pstmt.close();
                }
                if (c != null) {
                    returnConnection(c);
                }
            } catch (SQLException sql) {
                logger.error("SQLException in finally cleanup", sql);
            }
        }
    }

    public void insertReservation(String elasticReservationId,
                                   String groupId, String coschedId)
            throws Exception {

        Connection c = null;
        PreparedStatement pstmt = null;

        try {
            c = dataSource.getConnection();
            pstmt = c.prepareStatement(DefaultElasticPersistence.INSERT_RESERVATION);
            pstmt.setString(1, elasticReservationId);
            pstmt.setString(2, groupId);
            pstmt.setString(3, coschedId);

            int rc = pstmt.executeUpdate();
            if (rc != 1) {
                throw new Exception("did not insert the row properly");
            }
        } catch (SQLException e) {
            logger.error("", e);
            throw e;
        } finally {
            try {
                if (pstmt != null) {
                    pstmt.close();
                }
                if (c != null) {
                    returnConnection(c);
                }
            } catch (SQLException sql) {
                logger.error("SQLException in finally cleanup", sql);
            }
        }

    }

    public String selectIdFromId(String query, String id) {
        Connection c = null;
        PreparedStatement pstmt = null;
        ResultSet rs;

        try {
            c = dataSource.getConnection();
            pstmt = c.prepareStatement(query);
            pstmt.setString(1, id);
            rs = pstmt.executeQuery();
            if (!rs.next()) {
                return null;
            }
            return rs.getString(1);
        } catch (SQLException e) {
            logger.error("", e);
            return null;
        } finally {
            try {
                if (pstmt != null) {
                    pstmt.close();
                }
                if (c != null) {
                    returnConnection(c);
                }
            } catch (SQLException sql) {
                logger.error("SQLException in finally cleanup", sql);
            }
        }
    }

    private void returnConnection(Connection connection) {
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException e) {
                logger.error("", e);
            }
        }
    }

}
