// Nearly identical to graphql.schema.PropertyDataFetcher, but deals with arguments.
package com.distelli.graphql;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Map;

import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLFieldsContainer;
import graphql.schema.GraphQLNonNull;
import graphql.schema.GraphQLOutputType;
import graphql.schema.GraphQLType;

import static graphql.Scalars.GraphQLBoolean;

public class MethodDataFetcher implements DataFetcher {
    private final String propertyName;
    private final Class argType;
    private final Object impl;
    private String graphQLPropertyName = null;

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
    
    public MethodDataFetcher(String propertyName, Class argType, Object impl, String graphQLPropertyName) {
    		this(propertyName, argType, impl);
    		this.graphQLPropertyName = graphQLPropertyName;
    }

    @Override
    public Object get(DataFetchingEnvironment env) {
        Object source = ( null != impl ) ? impl : env.getSource();
        if (source == null) return null;
        if (source instanceof ResolveDataFetchingEnvironment) {
            source = ((ResolveDataFetchingEnvironment)source).resolve(env);
        }
        return getMethodViaGetter(source, env.getFieldType(), getFieldType(env.getParentType()), env.getArguments());
    }

    private GraphQLFieldDefinition getFieldType(GraphQLType type) {
        if ( type instanceof GraphQLFieldsContainer ) {
        		GraphQLFieldDefinition fieldType = ((GraphQLFieldsContainer)type).getFieldDefinition(propertyName);
        		
        		if (null == fieldType && null != this.graphQLPropertyName) {
        			fieldType = ((GraphQLFieldsContainer)type).getFieldDefinition(graphQLPropertyName);
        		}
        		
        		return fieldType;
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

    private static class MapInvocationHandler implements InvocationHandler {
        private Map<String, Object> map;
        public MapInvocationHandler(Map<String, Object> map) {
            this.map = map;
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
            Object result = map.get(lcArgName);
            if ( null == result ) {
                result = map.get(ucArgName);
            }
            if ( null == result ) return null;
            if ( Map.class.isAssignableFrom(result.getClass()) ) {
                Class returnType = method.getReturnType();
                if ( ! Map.class.isAssignableFrom(returnType) ) {
                    return Proxy.newProxyInstance(
                        returnType.getClassLoader(),
                        new Class[]{returnType},
                        new MapInvocationHandler((Map)result));
                }
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
                                       new MapInvocationHandler(args));
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
