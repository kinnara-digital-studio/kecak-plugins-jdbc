package com.kinnara.kecakplugins.jdbc;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.util.Properties;

import javax.sql.DataSource;

import org.apache.commons.dbcp.BasicDataSourceFactory;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.form.model.Element;
import org.joget.apps.form.model.FormBinder;
import org.joget.apps.form.model.FormData;
import org.joget.apps.form.model.FormLoadBinder;
import org.joget.apps.form.model.FormLoadElementBinder;
import org.joget.apps.form.model.FormLoadMultiRowElementBinder;
import org.joget.apps.form.model.FormRow;
import org.joget.apps.form.model.FormRowSet;
import org.joget.apps.form.service.FormUtil;
import org.joget.commons.util.LogUtil;

/**
 * 
 * @author aristo
 *
 */
public class JdbcLoadBinder extends FormBinder implements FormLoadBinder, FormLoadElementBinder, FormLoadMultiRowElementBinder {
    
    private final static String MESSAGE_PATH = "messages/JdbcLoadBinder";
    
    public String getName() {
        return "Kecak JDBC Load Binder";
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
    	return "Artifact ID : " + getClass().getPackage().getImplementationTitle();
    }

    public String getPropertyOptions() {
        return AppUtil.readPluginResource(getClassName(), "/properties/JdbcLoadBinder.json", new Object[] { JdbcTestConnectionApi.class.getName() }, true, MESSAGE_PATH);
    }

    public FormRowSet load(Element element, String primaryKey, FormData formData) {
        FormRowSet rows = new FormRowSet();
        rows.setMultiRow(true);
        
        //Check the sql. If require primary key and primary key is null, return empty result.
        String sql = getPropertyString("sql");
        if ((primaryKey == null || primaryKey.isEmpty()) && sql.contains("?")) {
            return rows;
        }
        
        try {
        	DataSource ds = createDataSource();
        	try (Connection con = ds.getConnection(); PreparedStatement pstmt = con.prepareStatement(sql); ) {
                //set query parameters
                if (sql.contains("?") && primaryKey != null && !primaryKey.isEmpty()) {
                    pstmt.setObject(1, primaryKey);
                }
                
                try( ResultSet rs = pstmt.executeQuery() ) {
                	
    	            ResultSetMetaData rsmd = rs.getMetaData();
    	            int columnsNumber = rsmd.getColumnCount();
    	            
    	            // Set retrieved result to Form Row Set
    	            while (rs.next()) {
    	                FormRow row = new FormRow();
    	                
    	                //get the name of each column as field id 
    	                for (int i = 1; i <= columnsNumber; i++) {
    	                    String name = rsmd.getColumnLabel(i);
    	                    String value = rs.getString(name);
    	                    
    	                    if (FormUtil.PROPERTY_ID.equals(name)) {
    	                        row.setId(value);
    	                    } else {
    	                        row.setProperty(name, (value != null)?value:"");
    	                        
    	                        //cater for form data column as well
    	                        if (name.startsWith("c_")) {
    	                            row.setProperty(name.replaceFirst("c_", ""), (value != null)?value:"");
    	                        }
    	                    }
    	                }
    	                
    	                rows.add(row);
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
