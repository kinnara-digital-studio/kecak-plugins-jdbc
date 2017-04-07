package com.kinnara.kecakplugins.jdbc;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.sql.DataSource;
import org.apache.commons.dbcp.BasicDataSourceFactory;
import org.joget.apps.app.model.AppDefinition;
import org.joget.apps.app.service.AppPluginUtil;
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
import org.joget.commons.util.DynamicDataSourceManager;
import org.joget.commons.util.LogUtil;
import org.joget.commons.util.SecurityUtil;
import org.joget.commons.util.UuidGenerator;
import org.joget.plugin.base.PluginWebSupport;
import org.joget.workflow.model.service.WorkflowUserManager;
import org.joget.workflow.util.WorkflowUtil;
import org.json.JSONObject;

/**
 * 
 * @author aristo
 *
 */
public class JdbcStoreBinder extends FormBinder implements FormStoreBinder, FormStoreElementBinder, FormStoreMultiRowElementBinder, PluginWebSupport {
    
    private final static String MESSAGE_PATH = "messages/JdbcStoreBinder";
    
    public String getName() {
        return "Kecak JDBC Store Binder";
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
        return AppUtil.readPluginResource(getClassName(), "/properties/jdbcStoreBinder.json", null, true, MESSAGE_PATH);
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
                
                message = AppPluginUtil.getMessage("form.jdbcStoreBinder.connectionOk", getClassName(), MESSAGE_PATH);
            } catch (Exception e) {
                LogUtil.error(getClassName(), e, "Test Connection error");
                message = AppPluginUtil.getMessage("form.jdbcStoreBinder.connectionFail", getClassName(), MESSAGE_PATH) + "\n" + e.getMessage();
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
