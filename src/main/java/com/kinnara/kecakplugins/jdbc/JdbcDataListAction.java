package com.kinnara.kecakplugins.jdbc;

import org.apache.commons.dbcp.BasicDataSourceFactory;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.datalist.model.DataList;
import org.joget.apps.datalist.model.DataListActionDefault;
import org.joget.apps.datalist.model.DataListActionResult;
import org.joget.commons.util.LogUtil;
import org.joget.plugin.base.PluginManager;
import org.joget.workflow.util.WorkflowUtil;

import javax.servlet.http.HttpServletRequest;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Optional;
import java.util.Properties;
import java.util.ResourceBundle;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author aristo
 *
 *
 */
public class JdbcDataListAction extends DataListActionDefault {
    @Override
    public String getLinkLabel() {
        return getPropertyString("label", "DB Action");
    }

    @Override
    public String getHref() {
        return getPropertyString("href");
    }

    @Override
    public String getTarget() {
        return "post";
    }

    @Override
    public String getHrefParam() {
        return getPropertyString("hrefParam");
    }

    @Override
    public String getHrefColumn() {
        return getPropertyString("hrefColumn");
    }

    @Override
    public String getConfirmation() {
        String confirm = getPropertyString("confirmation");
        if (confirm == null || confirm.isEmpty()) {
            confirm = "Please Confirm";
        }
        return confirm;
    }

    @Override
    public DataListActionResult executeAction(DataList dataList, String[] rowKeys) {
        HttpServletRequest request = WorkflowUtil.getHttpServletRequest();
        if (request != null && !"POST".equalsIgnoreCase(request.getMethod())) {
            return null;
        }

        DataListActionResult result = new DataListActionResult();
        result.setType(DataListActionResult.TYPE_REDIRECT);
        result.setUrl("REFERER");

        try {
            DataSource ds = createDataSource();
            String sql = getPropertyString("sql");
            try(Connection con = ds.getConnection();
                PreparedStatement ps = con.prepareStatement(prepareSqlQuery(sql))) {
                int numberOfRecords = 0;
                for(String rowKey : rowKeys) {
                    for(long i = 0, size = getSqlFieldIdCount(sql); i < size; i++) {
                        ps.setString((int) i + 1, rowKey);
                    }

                    try {
                        numberOfRecords += ps.executeUpdate();
                    } catch (SQLException e) {
                        LogUtil.error(getClassName(), e, e.getMessage());
                    }
                }

                LogUtil.info(getClass().getName(), "[" + numberOfRecords + "] records processed");
            }

        } catch (Exception e) {
            LogUtil.error(getClassName(), e, e.getMessage());
        }

        return result;
    }

    @Override
    public String getName() {
        return getLabel() + getVersion();
    }

    @Override
    public String getVersion() {
        PluginManager pluginManager = (PluginManager) AppUtil.getApplicationContext().getBean("pluginManager");
        ResourceBundle resourceBundle = pluginManager.getPluginMessageBundle(getClassName(), "/messages/BuildNumber");
        String buildNumber = resourceBundle.getString("build.number");
        return buildNumber;    }

    @Override
    public String getDescription() {
        return getClass().getPackage().getImplementationTitle();
    }

    @Override
    public String getLabel() {
        return "JDBC DataList Action";
    }

    @Override
    public String getClassName() {
        return getClass().getName();
    }

    @Override
    public String getPropertyOptions() {
        return AppUtil.readPluginResource(getClassName(), "/properties/JdbcDataListAction.json", new Object[] { JdbcTestConnectionApi.class.getName() }, true, "/messages/JdbcDataListAction");
    }

    @Override
    public String getPropertyString(String property) {
        return getPropertyString(property, "");
    }

    public String getPropertyString(String property, String defaultValue) {
        return Optional.of(property)
                .map(super::getPropertyString)
                .map(it -> AppUtil.processHashVariable(it, null, null, null))
                .orElse(defaultValue);
    }

    /**
     * To creates data source based on setting
     * @return
     * @throws Exception
     */
    protected DataSource createDataSource() throws Exception {
        DataSource ds;
        String datasource = getPropertyString("jdbcDatasource");
        if ("default".equals(datasource)) {
            // use current datasource
            ds = (DataSource)AppUtil.getApplicationContext().getBean("setupDataSource");
        } else {
            // use custom datasource
            Properties dsProps = new Properties();
            dsProps.put("driverClassName", getPropertyString("jdbcDriver"));
            dsProps.put("url", getPropertyString("jdbcUrl"));
            dsProps.put("username", getPropertyString("jdbcUser"));
            dsProps.put("password", getPropertyString("jdbcPassword"));
            ds = BasicDataSourceFactory.createDataSource(dsProps);
        }
        return ds;
    }

    /**
     * Replace string {id} with ?
     *
     * @param sql
     * @return
     */
    private String prepareSqlQuery(String sql) {
        return sql.replaceAll("\\{id}", "?");
    }

    /**
     * Count how many {id} in the sql string
     *
     * @param sql
     * @return
     */
    private long getSqlFieldIdCount(String sql) {
        Pattern p = Pattern.compile("\\{id}");
        Matcher m = p.matcher(sql);
        int n = 0;
        while(m.find()) n++;
        return n;
    }
}
