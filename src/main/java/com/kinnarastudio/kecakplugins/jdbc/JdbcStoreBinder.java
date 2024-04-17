package com.kinnarastudio.kecakplugins.jdbc;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Properties;
import java.util.ResourceBundle;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.sql.DataSource;

import org.apache.commons.dbcp.BasicDataSourceFactory;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.form.model.Element;
import org.joget.apps.form.model.Form;
import org.joget.apps.form.model.FormBinder;
import org.joget.apps.form.model.FormData;
import org.joget.apps.form.model.FormRow;
import org.joget.apps.form.model.FormRowSet;
import org.joget.apps.form.model.FormStoreBinder;
import org.joget.apps.form.model.FormStoreElementBinder;
import org.joget.apps.form.model.FormStoreMultiRowElementBinder;
import org.joget.apps.form.service.FormUtil;
import org.joget.commons.util.LogUtil;
import org.joget.commons.util.UuidGenerator;
import org.joget.plugin.base.PluginManager;

/**
 * 
 * @author aristo
 *
 */
public class JdbcStoreBinder extends FormBinder implements FormStoreBinder, FormStoreElementBinder, FormStoreMultiRowElementBinder {
    
    private final static String MESSAGE_PATH = "messages/JdbcStoreBinder";
    
    public String getName() {
        return getLabel();
    }

    public String getVersion() {
        PluginManager pluginManager = (PluginManager) AppUtil.getApplicationContext().getBean("pluginManager");
        ResourceBundle resourceBundle = pluginManager.getPluginMessageBundle(getClassName(), "/messages/BuildNumber");
        String buildNumber = resourceBundle.getString("build.number");
        return buildNumber;
    }
    
    public String getClassName() {
        return getClass().getName();
    }

    public String getLabel() {
        return "JDBC Store Binder";
    }
    
    public String getDescription() {
    	return "Kecak Plugins; Artifact ID : " + getClass().getPackage().getImplementationTitle();
    }

    public String getPropertyOptions() {
        return AppUtil.readPluginResource(getClassName(), "/properties/JdbcStoreBinder.json", new Object[] { JdbcTestConnectionApi.class.getName() }, true, MESSAGE_PATH);
    }

    public FormRowSet store(Element element, FormRowSet rows, FormData formData) {
        Form parentForm = FormUtil.findRootForm(element);
        String primaryKeyValue = parentForm.getPrimaryKeyValue(formData);
        
        try {
            DataSource ds = createDataSource();
            
            try(Connection con = ds.getConnection()) {            
	            //check for deletion
	            FormRowSet originalRowSet = formData.getLoadBinderData(element);
	            if (originalRowSet != null && !originalRowSet.isEmpty()) {
	                for (FormRow r : originalRowSet) {
	                    if (!rows.contains(r)) {
	                        String query = getPropertyString("delete_sql");
	                        try(PreparedStatement pstmt = con.prepareStatement(getQuery(query))) {
		                        int i = 1;
		                        for (String obj : getParams(query, r, primaryKeyValue)) {
		                            pstmt.setObject(i, obj);
		                            i++;
		                        }
		                        pstmt.executeUpdate();
	                        }
	                    }
	                }
	            }
	            
	            if (!(rows == null || rows.isEmpty())) {
	                //run query for each row
	                for (FormRow row : rows) {
	                    //check to use insert query or update query
	                    String checkSql = getPropertyString("check_sql");
	                    try(PreparedStatement pstmt = con.prepareStatement(getQuery(checkSql))) {
		                    int i = 1;
		                    for (String obj : getParams(checkSql, row, primaryKeyValue)) {
		                        pstmt.setObject(i, obj);
		                        i++;
		                    }
		
		                    String query = getPropertyString("insert_sql");
		                    
		                    try(ResultSet rs = pstmt.executeQuery()) {
			                    //record exist, use update query
			                    if (rs.next()) {
			                        query = getPropertyString("update_sql");
			                        
			                        try(PreparedStatement pstmtUpdate = con.prepareStatement(getQuery(query))) {
					                    i = 1;
					                    for (String obj : getParams(query, row, primaryKeyValue)) {
					                    	pstmtUpdate.setObject(i, obj);
					                        i++;
					                    }
					                    pstmtUpdate.executeUpdate();
				                    }
			                    }
		                    }
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
     * Used to replaces all syntax like {field_id} to question mark
     * @param query
     * @return 
     */
    protected String getQuery(String query) {
        return query.replaceAll("\\{[a-zA-Z0-9_]+\\}", "?");
    }
    
    /**
     * Used to retrieves the value of variables in query 
     * @param query
     * @param row
     * @return 
     */
    protected Collection<String> getParams(String query, FormRow row, String primaryKey) {
        Collection<String> params = new ArrayList<String>();
        
        Pattern pattern = Pattern.compile("\\{([a-zA-Z0-9_]+)\\}");
        Matcher matcher = pattern.matcher(query);
        
        while (matcher.find()) {
            String key = matcher.group(1);
            
            if (FormUtil.PROPERTY_ID.equals(key)) {
                String value = row.getId();
                if (value == null || value.isEmpty()) {
                    value = UuidGenerator.getInstance().getUuid();
                    row.setId(value);
                }
                params.add(value);
            } else if ("uuid".equals(key)) {
                params.add(UuidGenerator.getInstance().getUuid());
            } else if ("foreignKey".equals(key)) {
                params.add(primaryKey);
            } else {
                String value = row.getProperty(key);
                params.add((value != null)?value:"");
            }
        }
        
        return params;
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
