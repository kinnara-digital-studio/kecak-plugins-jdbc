package com.kinnarastudio.kecakplugins.jdbc.process.tool;

import com.kinnarastudio.kecakplugins.jdbc.app.webservice.JdbcTestConnectionApi;
import oracle.sql.TIMESTAMP;
import org.joget.apps.app.lib.DatabaseUpdateTool;
import org.joget.apps.app.service.AppUtil;
import org.joget.commons.util.DynamicDataSourceManager;
import org.joget.commons.util.LogUtil;
import org.joget.plugin.base.PluginManager;
import org.joget.workflow.model.WorkflowAssignment;
import org.joget.workflow.model.service.WorkflowManager;
import org.joget.workflow.util.WorkflowUtil;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 *
 */
public class JdbcTool extends DatabaseUpdateTool {
    public final static String LABEL = "JDBC Tool";

    @Override
    public String getName() {
        return LABEL;
    }

    @Override
    public String getVersion() {
        PluginManager pluginManager = (PluginManager) AppUtil.getApplicationContext().getBean("pluginManager");
        ResourceBundle resourceBundle = pluginManager.getPluginMessageBundle(getClassName(), "/messages/BuildNumber");
        String buildNumber = resourceBundle.getString("build.number");
        return buildNumber;
    }

    @Override
    public String getDescription() {
        return getClass().getPackage().getImplementationTitle();
    }

    @Override
    public Object execute(Map properties) {
        Object result;
        try {
            String query = (String) properties.get("query");
            String driver;
            DataSource ds;
            String datasource = (String)properties.get("jdbcDatasource");
            if ("default".equals(datasource)) {
                // use current datasource
                ds = (DataSource)AppUtil.getApplicationContext().getBean("setupDataSource");
                driver = DynamicDataSourceManager.getProperty("workflowDriver");
            } else {
                Properties props = new Properties();
                String driverClassName = (String) properties.get("driverClassName");
                String url = (String) properties.get("url");
                String username = (String) properties.get("username");
                String password = (String) properties.get("password");

                driver = driverClassName;

                // use custom datasource
                props.put("driverClassName", driverClassName);
                props.put("url", url);
                props.put("username", username);
                props.put("password", password);
                ds = createDataSource(props);
            }

            WorkflowAssignment wfAssignment = (WorkflowAssignment) properties.get("workflowAssignment");

            Map<String, String> replace = new HashMap<String, String>();
            if (driver.equalsIgnoreCase("com.mysql.cj.jdbc.Driver")) {
                replace.put("\\\\", "\\\\");
                replace.put("'", "\\'");
            } else {
                replace.put("'", "''");
            }

            query = WorkflowUtil.processVariable(query, null, wfAssignment, "regex", replace);

            if("SELECT".equalsIgnoreCase(getPropertyType())) {
                WorkflowManager workflowManager = (WorkflowManager) AppUtil.getApplicationContext().getBean("workflowManager");

                List<Map<String, String>> var = executeQuerySelect(ds, query, null, null, null);

                var.stream().findFirst().map(Map::entrySet).map(Collection::stream).orElseGet(Stream::empty).forEach(e -> {
                    workflowManager.activityVariable(e.getKey(), e.getValue(), null);
                });


                result = var;
            } else {
                result = executeQuery(ds, query);
            }
            return result;
        } catch (Exception e) {
            LogUtil.error(getClass().getName(), e, "Error executing plugin");
            return null;
        }
    }

    @Override
    public String getLabel() {
        return LABEL;
    }

    @Override
    public String getClassName() {
        return getClass().getName();
    }

    @Override
    public String getPropertyOptions() {
        return AppUtil.readPluginResource(getClass().getName(), "/properties/process/tool/JdbcTool.json", new Object[] { JdbcTestConnectionApi.class.getName() }, true, "/messages/JdbcTool");
    }

    protected List<Map<String, String>> executeQuerySelect(DataSource ds, String sql, String[] values, Integer start, Integer rows) throws SQLException {
        List<Map<String, String>> results = new ArrayList<>();
        try (Connection con = ds.getConnection();
             PreparedStatement pstmt = con.prepareStatement(sql)) {
            if (start == null || start < 0) {
                start = 0;
            }
            if (rows != null && rows != -1) {
                int totalRowsToQuery = start + rows;
                pstmt.setMaxRows(totalRowsToQuery);
            }
            if (values != null && values.length > 0) {
                for (int i = 0; i < values.length; ++i) {
                    pstmt.setObject(i + 1, values[i]);
                }
            }
            try (ResultSet rs = pstmt.executeQuery()) {
                int count = 0;
                while (rs.next()) {
                    Map<String, String> row = new HashMap<>();
                    if (count++ < start) {
                        continue;
                    }

                    Map<String, String> columns = getPropertyResultSet();
                    if (columns != null) {
                        for (Map.Entry<String, String> column : columns.entrySet()) {
                            String columnName = column.getKey();
                            Object obj = rs.getObject(columnName);
                            String columnValue = obj != null ? obj.toString() : "";
                            if (obj instanceof TIMESTAMP) {
                                TIMESTAMP timestamp = (TIMESTAMP) obj;
                                columnValue = timestamp.stringValue();
                            }
                            row.put(columnName, columnValue);
                        }
                    }
                    results.add(row);
                }
            }
        }
        return results;
    }

    protected Map<String, String> getPropertyResultSet() {
        return Optional.of(getPropertyGrid("resultSet"))
                .map(Arrays::stream)
                .orElseGet(Stream::empty)
                .collect(Collectors.toMap(m -> m.get("column"), m -> m.get("wfVariable")));
    }

    protected String getPropertyType() {
        return getPropertyString("type");
    }
}
