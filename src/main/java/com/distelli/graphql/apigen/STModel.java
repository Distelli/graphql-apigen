package com.distelli.graphql.apigen;

import java.util.Set;
import java.util.TreeSet;
import graphql.language.Type;
import graphql.language.FieldDefinition;
import graphql.language.ListType;
import graphql.language.NonNullType;
import graphql.language.TypeName;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Collection;

public class STModel {
    private static Map<String, String> BUILTINS = new HashMap<String, String>(){{
            put("Int", null);
            put("Long", null);
            put("Float", null);
            put("String", null);
            put("Boolean", null);
            put("ID", null);
            put("BigInteger", "java.math.BigInteger");
            put("BigDecimal", "java.math.BigDecimal");
            put("Byte", null);
            put("Short", null);
            put("Char", null);
        }};
    private static Map<String, String> RENAME = new HashMap<String, String>(){{
            put("Int", "Integer");
            put("ID", "String");
            put("Char", "Character");
        }};
    public static class Builder {
        private TypeEntry typeEntry;
        private Map<String, TypeEntry> referenceTypes;
        public Builder withTypeEntry(TypeEntry typeEntry) {
            this.typeEntry = typeEntry;
            return this;
        }
        public Builder withReferenceTypes(Map<String, TypeEntry> referenceTypes) {
            this.referenceTypes = referenceTypes;
            return this;
        }
        public STModel build() {
            return new STModel(this);
        }
    }
    private TypeEntry typeEntry;
    private Map<String, TypeEntry> referenceTypes;
    private List<Field> fields;
    private List<String> imports;
    private Field idField;
    private boolean gotIdField = false;
    private STModel(Builder builder) {
        this.typeEntry = builder.typeEntry;
        this.referenceTypes = builder.referenceTypes;
    }

    public String getPackageName() {
        return typeEntry.getPackageName();
    }

    public String getName() {
        return typeEntry.getName();
    }

    public String getUcname() {
        return ucFirst(getName());
    }

    private static String ucFirst(String name) {
        if ( null == name || name.length() < 1 ) return name;
        return name.substring(0, 1).toUpperCase() + name.substring(1);
    }

    public synchronized List<String> getImports() {
        if ( null == imports ) {
            Set<String> names = new TreeSet<String>();
            for ( FieldDefinition fieldDef : typeEntry.getObjectTypeDefinition().getFieldDefinitions() ) {
                addTypeNames(names, fieldDef.getType());
            }
            imports = new ArrayList<>(names);
        }
        return imports;
    }

    public synchronized Field getIdField() {
        if ( ! gotIdField ) {
            for ( Field field : getFields() ) {
                if ( "id".equals(field.name) ) {
                    idField = field;
                    break;
                }
            }
            gotIdField = true;
        }
        return idField;
    }

    public static class Field {
        public String name;
        public String type;
        public Field(String name, String type) {
            this.name = name;
            this.type = type;
        }
        public String getUcname() {
            return ucFirst(name);
        }
    }

    public synchronized List<Field> getFields() {
        if ( null == fields ) {
            fields = new ArrayList<>();
            for ( FieldDefinition fieldDef : typeEntry.getObjectTypeDefinition().getFieldDefinitions() ) {
                fields.add(new Field(fieldDef.getName(), toJavaTypeName(fieldDef.getType())));
            }
        }
        return fields;
    }

    private static String toJavaTypeName(Type type) {
        if ( type instanceof ListType ) {
            return "List<" + toJavaTypeName(((ListType)type).getType()) + ">";
        } else if ( type instanceof NonNullType ) {
            return toJavaTypeName(((NonNullType)type).getType());
        } else if ( type instanceof TypeName ) {
            String name = ((TypeName)type).getName();
            String rename = RENAME.get(name);
            if ( null != rename ) return rename;
            return name;
        } else {
            System.err.println("Unknown Type="+type.getClass().getName());
        }
        return null;
    }

    private void addTypeNames(Collection<String> names, Type type) {
        if ( type instanceof ListType ) {
            names.add("java.util.List");
            addTypeNames(names, ((ListType)type).getType());
        } else if ( type instanceof NonNullType ) {
            addTypeNames(names, ((NonNullType)type).getType());
        } else if ( type instanceof TypeName ) {
            String name = ((TypeName)type).getName();
            if ( BUILTINS.containsKey(name) ) {
                String importName = BUILTINS.get(name);
                if ( null == importName ) return;
                names.add(importName);
            } else {
                TypeEntry refEntry = referenceTypes.get(name);
                if ( null == refEntry ) {
                    System.err.println("Unknown type '"+name+"' was not defined in the schema");
                } else {
                    names.add(refEntry.getPackageName() + "." + name);
                }
            }
        } else {
            System.err.println("Unknown Type="+type.getClass().getName());
        }
    }
}
