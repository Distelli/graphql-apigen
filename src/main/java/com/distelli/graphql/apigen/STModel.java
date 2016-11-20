package com.distelli.graphql.apigen;

import java.util.Set;
import java.util.TreeSet;
import graphql.language.InterfaceTypeDefinition;
import graphql.language.EnumTypeDefinition;
import graphql.language.ScalarTypeDefinition;
import graphql.language.UnionTypeDefinition;
import graphql.language.InputObjectTypeDefinition;
import graphql.language.Type;
import graphql.language.ObjectTypeDefinition;
import graphql.language.Definition;
import graphql.language.FieldDefinition;
import graphql.language.ListType;
import graphql.language.NonNullType;
import graphql.language.TypeName;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

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
    // Field of Interface, Object, InputObject, UnionType (no names), Enum (no types)
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

    public void validate() {
        // TODO: Validate that any Object "implements" actually implements
        // the interface so we can error before compile time...

        // these throw if there are any inconsistencies...
        getFields();
        getImports();
    }

    public boolean isObjectType() {
        return typeEntry.getDefinition() instanceof ObjectTypeDefinition;
    }

    public boolean isInterfaceType() {
        return typeEntry.getDefinition() instanceof InterfaceTypeDefinition;
    }

    public boolean isEnumType() {
        return typeEntry.getDefinition() instanceof EnumTypeDefinition;
    }

    public boolean isScalarType() {
        return typeEntry.getDefinition() instanceof ScalarTypeDefinition;
    }

    public boolean isUnionType() {
        return typeEntry.getDefinition() instanceof UnionTypeDefinition;
    }

    public boolean isInputObjectType() {
        return typeEntry.getDefinition() instanceof InputObjectTypeDefinition;
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

    public synchronized List<String> getImports() {
        if ( null == imports ) {
            Definition def = typeEntry.getDefinition();
            Set<String> names = new TreeSet<String>();
            if ( def instanceof ObjectTypeDefinition ) {
                addImports(names, (ObjectTypeDefinition)def);
            }
            imports = new ArrayList<>(names);
        }
        return imports;
    }

    public synchronized List<Field> getFields() {
        if ( null == fields ) {
            Definition def = typeEntry.getDefinition();
            if ( def instanceof ObjectTypeDefinition ) {
                fields = getFields((ObjectTypeDefinition)def);
            } else {
                fields = Collections.emptyList();
            }
        }
        return fields;
    }

    private static List<Field> getFields(ObjectTypeDefinition def) {
        List<Field> fields = new ArrayList<Field>();
        for ( FieldDefinition fieldDef : def.getFieldDefinitions() ) {
            fields.add(new Field(fieldDef.getName(), toJavaTypeName(fieldDef.getType())));
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

    private void addImports(Collection<String> imports, ObjectTypeDefinition def) {
        for ( FieldDefinition fieldDef : def.getFieldDefinitions() ) {
            addImports(imports, fieldDef.getType());
        }
    }

    private void addImports(Collection<String> imports, Type type) {
        if ( type instanceof ListType ) {
            imports.add("java.util.List");
            addImports(imports, ((ListType)type).getType());
        } else if ( type instanceof NonNullType ) {
            addImports(imports, ((NonNullType)type).getType());
        } else if ( type instanceof TypeName ) {
            String name = ((TypeName)type).getName();
            if ( BUILTINS.containsKey(name) ) {
                String importName = BUILTINS.get(name);
                if ( null == importName ) return;
                imports.add(importName);
            } else {
                TypeEntry refEntry = referenceTypes.get(name);
                if ( null == refEntry ) {
                    throw new RuntimeException("Unknown type '"+name+"' was not defined in the schema");
                } else {
                    imports.add(refEntry.getPackageName() + "." + name);
                }
            }
        } else {
            throw new RuntimeException("Unknown Type="+type.getClass().getName());
        }
    }
}
