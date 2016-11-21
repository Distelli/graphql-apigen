// Nearly identical to graphql.schema.PropertyDataFetcher, but deals with arguments.
package com.distelli.graphql;

import graphql.schema.GraphQLOutputType;
import graphql.schema.GraphQLType;
import graphql.schema.GraphQLNonNull;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLFieldsContainer;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.lang.reflect.InvocationHandler;
import java.util.Map;

import static graphql.Scalars.GraphQLBoolean;

public class MethodDataFetcher implements DataFetcher {

    private final String propertyName;
    private final Class argType;
    private final Object impl;

    public MethodDataFetcher(String propertyName, Class argType, Object impl) {
        if ( null != argType ) {
            if ( ! argType.isInterface() ) {
                throw new IllegalArgumentException("argType must be interface, got argType="+argType);
            }
        }
        this.propertyName = propertyName;
        this.argType = argType;
        this.impl = impl;
    }

    @Override
    public Object get(DataFetchingEnvironment env) {
        Object source = ( null != impl ) ? impl : env.getSource();
        if (source == null) return null;
        return getMethodViaGetter(source, env.getFieldType(), getFieldType(env.getParentType()), env.getArguments());
    }

    private GraphQLFieldDefinition getFieldType(GraphQLType type) {
        if ( type instanceof GraphQLFieldsContainer ) {
            return ((GraphQLFieldsContainer)type).getFieldDefinition(propertyName);
        }
        return null;
    }

    private Object getMethodViaGetter(Object object, GraphQLOutputType outputType, GraphQLFieldDefinition fieldDef, Map<String, Object> args) {
        if ( fieldDef.getArguments().size() > 0 ^ null != argType ) {
            throw new IllegalStateException(
                "MethodDataFetcher created has argType="+argType+
                " and invoked with argSize="+fieldDef.getArguments().size()+
                ", argType must be null if argSize == 0; or argType must be non null and argSize > 0");
        }
        try {
            if (fieldDef.getArguments().size() > 0) {
                return getMethodViaGetterUsingPrefix(object, args, null);
            } else if (isBooleanMethod(outputType)) {
                try {
                    return getMethodViaGetterUsingPrefix(object, args, "is");
                } catch (NoSuchMethodException ex) {
                    return getMethodViaGetterUsingPrefix(object, args, "get");
                }
            } else {
                return getMethodViaGetterUsingPrefix(object, args, "get");
            }
        } catch (NoSuchMethodException ex) {
            throw new RuntimeException(ex);
        }
    }

    private static class ArgsInvocationHandler implements InvocationHandler {
        private Map<String, Object> args;
        public ArgsInvocationHandler(Map<String, Object> args) {
            this.args = args;
        }
        public Object invoke(Object proxy, Method method, Object[] methodArgs) {
            if ( null != methodArgs && methodArgs.length > 0 ) {
                throw new UnsupportedOperationException(
                    "Expected all interface methods to have no arguments, got "+methodArgs.length+" arguments");
            }
            String name = method.getName();
            if ( ! name.startsWith("get") || name.length() < 4 ) {
                throw new UnsupportedOperationException(
                    "Expected all interface methods to begin with 'get', got "+name);
            }
            String ucArgName = name.substring(3);
            String lcArgName = ucArgName.substring(0, 1).toLowerCase() + ucArgName.substring(1);
            Object result = args.get(lcArgName);
            if ( null == result ) {
                result = args.get(ucArgName);
            }
            return result;
        }
    }

    private Object getMethodViaGetterUsingPrefix(Object object, Map<String, Object> args, String prefix)
        throws NoSuchMethodException
    {
        String methodName;
        if ( null == prefix ) {
            methodName = propertyName;
        } else {
            methodName = prefix + propertyName.substring(0, 1).toUpperCase() + propertyName.substring(1);
        }
        try {
            Method method;
            if ( null == argType ) {
                method = object.getClass().getMethod(methodName);
                return method.invoke(object);
            }
            method = object.getClass().getMethod(methodName, argType);
            Object argsProxy =
                Proxy.newProxyInstance(argType.getClassLoader(),
                                       new Class[]{argType},
                                       new ArgsInvocationHandler(args));
            return method.invoke(object, argsProxy);
        } catch (IllegalAccessException|InvocationTargetException ex) {
            throw new RuntimeException(ex);
        }
    }

    private boolean isBooleanMethod(GraphQLOutputType outputType) {
        if (outputType == GraphQLBoolean) return true;
        if (outputType instanceof GraphQLNonNull) {
            return ((GraphQLNonNull) outputType).getWrappedType() == GraphQLBoolean;
        }
        return false;
    }

    @Override
    public String toString() {
        return "MethodDataFetcher{"+
            "propertyName="+propertyName+
            ", argType="+argType+
            "}";
    }
}
