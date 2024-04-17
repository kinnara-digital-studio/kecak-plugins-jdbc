package com.kinnarastudio.kecakplugins.jdbc;

import org.apache.commons.dbcp.BasicDataSourceFactory;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.form.model.*;
import org.joget.apps.form.service.FormUtil;
import org.joget.commons.util.LogUtil;
import org.joget.plugin.base.PluginManager;
import org.joget.workflow.model.WorkflowAssignment;
import org.joget.workflow.model.service.WorkflowManager;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.util.Optional;
import java.util.Properties;
import java.util.ResourceBundle;

/**
 * 
 * @author aristo
 *
 */
public class JdbcOptionsBinder extends FormBinder implements FormLoadOptionsBinder, FormAjaxOptionsBinder { 
    private final static String MESSAGE_PATH = "messages/JdbcOptionsBinder";

    @Override
    public String getName() {
        return getLabel();
    }

    @Override
    public String getVersion() {
        PluginManager pluginManager = (PluginManager) AppUtil.getApplicationContext().getBean("pluginManager");
        ResourceBundle resourceBundle = pluginManager.getPluginMessageBundle(getClassName(), "/messages/BuildNumber");
        String buildNumber = resourceBundle.getString("build.number");
        return buildNumber;
    }

    @Override
    public String getClassName() {
        return getClass().getName();
    }

    @Override
    public String getLabel() {
        return "JDBC Option Binder";
    }

    @Override
    public String getDescription() {
    	return "Kecak Plugins; Artifact ID : " + getClass().getPackage().getImplementationTitle();
    }

    @Override
    public String getPropertyOptions() {
        return AppUtil.readPluginResource(getClassName(), "/properties/JdbcOptionsBinder.json", new Object[] { JdbcTestConnectionApi.class.getName() }, true, "/messages/JdbcOptionsBinder");
    }

    @Override
    public FormRowSet load(Element element, String primaryKey, FormData formData) {
        setFormData(formData);
        return loadAjaxOptions(null); // reuse loadAjaxOptions method
    }

    @Override
    public boolean useAjax() {
        return "true".equalsIgnoreCase(getPropertyString("useAjax")); // let user to decide whether or not to use ajax for dependency field
    }

    @Override
    public FormRowSet loadAjaxOptions(String[] dependencyValues) {
        WorkflowManager workflowManager = (WorkflowManager) AppUtil.getApplicationContext().getBean("workflowManager");

        WorkflowAssignment workflowAssignment = Optional.ofNullable(getFormData())
                .map(FormData::getActivityId)
                .map(workflowManager::getAssignment)
                .orElse(null);

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
        String sql = AppUtil.processHashVariable(getPropertyString("sql"), workflowAssignment, null, null);
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
}
