package com.kinnara.kecakplugins.jdbc;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.util.Properties;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.sql.DataSource;

import org.apache.commons.dbcp.BasicDataSourceFactory;
import org.joget.apps.app.model.AppDefinition;
import org.joget.apps.app.service.AppPluginUtil;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.form.model.Element;
import org.joget.apps.form.model.FormAjaxOptionsBinder;
import org.joget.apps.form.model.FormBinder;
import org.joget.apps.form.model.FormData;
import org.joget.apps.form.model.FormLoadOptionsBinder;
import org.joget.apps.form.model.FormRow;
import org.joget.apps.form.model.FormRowSet;
import org.joget.apps.form.service.FormUtil;
import org.joget.commons.util.DynamicDataSourceManager;
import org.joget.commons.util.LogUtil;
import org.joget.commons.util.SecurityUtil;
import org.joget.plugin.base.PluginWebSupport;
import org.joget.workflow.model.service.WorkflowUserManager;
import org.joget.workflow.util.WorkflowUtil;
import org.json.JSONObject;

/**
 * 
 * @author aristo
 *
 */
public class JdbcOptionsBinder extends FormBinder implements FormLoadOptionsBinder, FormAjaxOptionsBinder, PluginWebSupport {

    private final static String MESSAGE_PATH = "messages/JdbcOptionsBinder";
    
    public String getName() {
        return "Kecak JDBC Option Binder";
    }

    public String getVersion() {
    	return getClass().getPackage().getImplementationVersion();
    }
    
    public String getClassName() {
        return getClass().getName();
    }

    public String getLabel() {
        return getName();
    }
    
    public String getDescription() {
    	return "Artifact ID : kecak-plugins-jdbc";
    }

    public String getPropertyOptions() {
        return AppUtil.readPluginResource(getClassName(), "/properties/jdbcOptionsBinder.json", null, true, MESSAGE_PATH);
    }

    public FormRowSet load(Element element, String primaryKey, FormData formData) {
        return loadAjaxOptions(null); // reuse loadAjaxOptions method
    }

    public boolean useAjax() {
        return "true".equalsIgnoreCase(getPropertyString("useAjax")); // let user to decide whether or not to use ajax for dependency field
    }

    public FormRowSet loadAjaxOptions(String[] dependencyValues) {
        FormRowSet rows = new FormRowSet();
        rows.setMultiRow(true);
        
        //add empty option based on setting
        if ("true".equals(getPropertyString("addEmpty"))) {
            FormRow empty = new FormRow();
            empty.setProperty(FormUtil.PROPERTY_LABEL, getPropertyString("emptyLabel"));
            empty.setProperty(FormUtil.PROPERTY_VALUE, "");
            rows.add(empty);
        }
        
        //Check the sql. If require dependency value and dependency value is not exist, return empty result.
        String sql = getPropertyString("sql");
        if ((dependencyValues == null || dependencyValues.length == 0) && sql.contains("?")) {
            return rows;
        }
        
        try {
            DataSource ds = createDataSource();
            try(Connection con = ds.getConnection()) {
	            //support for multiple dependency values
	            if (sql.contains("?") && dependencyValues != null && dependencyValues.length > 1) {
	                String mark = "?";
	                for (int i = 1; i < dependencyValues.length; i++) {
	                    mark += ", ?";
	                }
	                sql = sql.replace("?", mark);
	            }
	            
	            try(PreparedStatement pstmt = con.prepareStatement(sql)) {
		            //set query parameters
		            if (sql.contains("?") && dependencyValues != null && dependencyValues.length > 0) {
		                for (int i = 0; i < dependencyValues.length; i++) {
		                    pstmt.setObject(i + 1, dependencyValues[i]);
		                }
		            }

		            try(ResultSet rs = pstmt.executeQuery()) {
			            ResultSetMetaData rsmd = rs.getMetaData();
			
			            int columnsNumber = rsmd.getColumnCount();
			            
			            // Set retrieved result to Form Row Set
			            while (rs.next()) {
			                FormRow row = new FormRow();
			                
			                String value = rs.getString(1);
			                String label = rs.getString(2);
			                
			                row.setProperty(FormUtil.PROPERTY_VALUE, (value != null)?value:"");
			                row.setProperty(FormUtil.PROPERTY_LABEL, (label != null)?label:"");
			                
			                if (columnsNumber > 2) {
			                    String grouping = rs.getString(3);
			                    row.setProperty(FormUtil.PROPERTY_GROUPING, grouping);
			                }
			                
			                rows.add(row);
			            }
		            }
	            }
            }
        } catch (Exception e) {
            LogUtil.error(getClassName(), e, "");
        }
        
        return rows;
    }
    
    /**
     * To creates data source based on setting
     * @return
     * @throws Exception 
     */
    protected DataSource createDataSource() throws Exception {
        DataSource ds = null;
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
     * JSON API for test connection button
     * @param request
     * @param response
     * @throws ServletException
     * @throws IOException 
     */
    public void webService(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        //Limit the API for admin usage only
        boolean isAdmin = WorkflowUtil.isCurrentUserInRole(WorkflowUserManager.ROLE_ADMIN);
        if (!isAdmin) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }
        
        String action = request.getParameter("action");
        if ("testConnection".equals(action)) {
            String message = "";
            Connection conn = null;
            try {
                AppDefinition appDef = AppUtil.getCurrentAppDefinition();
                
                String jdbcDriver = AppUtil.processHashVariable(request.getParameter("jdbcDriver"), null, null, null, appDef);
                String jdbcUrl = AppUtil.processHashVariable(request.getParameter("jdbcUrl"), null, null, null, appDef);
                String jdbcUser = AppUtil.processHashVariable(request.getParameter("jdbcUser"), null, null, null, appDef);
                String jdbcPassword = AppUtil.processHashVariable(SecurityUtil.decrypt(request.getParameter("jdbcPassword")), null, null, null, appDef);
                
                Properties dsProps = new Properties();
                dsProps.put("driverClassName", jdbcDriver);
                dsProps.put("url", jdbcUrl);
                dsProps.put("username", jdbcUser);
                dsProps.put("password", jdbcPassword);
                DataSource ds = BasicDataSourceFactory.createDataSource(dsProps);
                
                conn = ds.getConnection();
                
                message = AppPluginUtil.getMessage("form.jdbcOptionsBinder.connectionOk", getClassName(), MESSAGE_PATH);
            } catch (Exception e) {
                LogUtil.error(getClassName(), e, "Test Connection error");
                message = AppPluginUtil.getMessage("form.jdbcOptionsBinder.connectionFail", getClassName(), MESSAGE_PATH) + "\n" + e.getMessage();
            } finally {
                try {
                    if (conn != null && !conn.isClosed()) {
                        conn.close();
                    }
                } catch (Exception e) {
                    LogUtil.error(DynamicDataSourceManager.class.getName(), e, "");
                }
            }
            try {
                JSONObject jsonObject = new JSONObject();
                jsonObject.accumulate("message", message);
                jsonObject.write(response.getWriter());
            } catch (Exception e) {
                //ignore
            }
        } else {
            response.setStatus(HttpServletResponse.SC_NO_CONTENT);
        }
    }
}
