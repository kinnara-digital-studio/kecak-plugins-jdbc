package com.kinnara.kecakplugins.jdbc;

import java.util.ArrayList;
import java.util.Collection;

import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;

public class Activator implements BundleActivator {

    protected Collection<ServiceRegistration> registrationList;

    public void start(BundleContext context) {
        registrationList = new ArrayList<ServiceRegistration>();

        //Register plugin here
        registrationList.add(context.registerService(JdbcLoadBinder.class.getName(), new JdbcLoadBinder(), null));
        registrationList.add(context.registerService(JdbcStoreBinder.class.getName(), new JdbcStoreBinder(), null));
        registrationList.add(context.registerService(JdbcOptionsBinder.class.getName(), new JdbcOptionsBinder(), null));
        registrationList.add(context.registerService(JdbcDatalistBinder.class.getName(), new JdbcDatalistBinder(), null));
        registrationList.add(context.registerService(JdbcTestConnectionApi.class.getName(), new JdbcTestConnectionApi(), null));
    }

    public void stop(BundleContext context) {
        for (ServiceRegistration registration : registrationList) {
            registration.unregister();
        }
    }
}