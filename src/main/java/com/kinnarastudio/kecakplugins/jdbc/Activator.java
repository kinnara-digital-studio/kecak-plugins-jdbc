package com.kinnarastudio.kecakplugins.jdbc;

import java.util.ArrayList;
import java.util.Collection;

import com.kinnarastudio.kecakplugins.jdbc.datalist.action.JdbcDataListAction;
import com.kinnarastudio.kecakplugins.jdbc.datalist.binder.JdbcDataListBinder;
import com.kinnarastudio.kecakplugins.jdbc.form.binder.JdbcLoadBinder;
import com.kinnarastudio.kecakplugins.jdbc.form.binder.JdbcOptionsBinder;
import com.kinnarastudio.kecakplugins.jdbc.form.binder.JdbcStoreBinder;
import com.kinnarastudio.kecakplugins.jdbc.process.tool.JdbcTool;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;

public class Activator implements BundleActivator {

    protected Collection<ServiceRegistration> registrationList;

    public void start(BundleContext context) {
        registrationList = new ArrayList<ServiceRegistration>();

        //Register plugin here
        registrationList.add(context.registerService(JdbcTool.class.getName(), new JdbcTool(), null));
        registrationList.add(context.registerService(JdbcLoadBinder.class.getName(), new JdbcLoadBinder(), null));
        registrationList.add(context.registerService(JdbcStoreBinder.class.getName(), new JdbcStoreBinder(), null));
        registrationList.add(context.registerService(JdbcOptionsBinder.class.getName(), new JdbcOptionsBinder(), null));
        registrationList.add(context.registerService(JdbcDataListBinder.class.getName(), new JdbcDataListBinder(), null));
        registrationList.add(context.registerService(JdbcTestConnectionApi.class.getName(), new JdbcTestConnectionApi(), null));
        registrationList.add(context.registerService(JdbcDataListAction.class.getName(), new JdbcDataListAction(), null));
    }

    public void stop(BundleContext context) {
        for (ServiceRegistration registration : registrationList) {
            registration.unregister();
        }
    }
}