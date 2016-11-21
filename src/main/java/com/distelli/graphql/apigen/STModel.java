package com.distelli.graphql.apigen;

import graphql.language.Definition;
import graphql.language.EnumTypeDefinition;
import graphql.language.EnumValueDefinition;
import graphql.language.FieldDefinition;
import graphql.language.InputObjectTypeDefinition;
import graphql.language.InputValueDefinition;
import graphql.language.InterfaceTypeDefinition;
import graphql.language.ListType;
import graphql.language.NonNullType;
import graphql.language.ObjectTypeDefinition;
import graphql.language.OperationTypeDefinition;
import graphql.language.ScalarTypeDefinition;
import graphql.language.SchemaDefinition;
import graphql.language.Type;
import graphql.language.TypeName;
import graphql.language.UnionTypeDefinition;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

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
    public static class DataFetcher {
        public String fieldName;
        public String fieldType;
        public String create;
    }
    // Field of Interface, Object, InputObject, UnionType (no names), Enum (no types)
    public static class Field {
        public String name;
        public String type;
        public DataFetcher dataFetcher;
        public String graphQLType;
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

    public boolean isSchemaType() {
        return typeEntry.getDefinition() instanceof SchemaDefinition;
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

    private static String lcFirst(String name) {
        if ( null == name || name.length() < 1 ) return name;
        return name.substring(0, 1).toLowerCase() + name.substring(1);
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

    public List<DataFetcher> getDataFetchers() {
        Map<String, DataFetcher> fetchers = new LinkedHashMap<>();
        for ( Field field : getFields() ) {
            DataFetcher fetcher = field.dataFetcher;
            if ( null == fetcher ) continue;
            fetchers.put(fetcher.fieldType, fetcher);
        }
        return new ArrayList<>(fetchers.values());
    }

    public synchronized List<String> getImports() {
        if ( null == imports ) {
            Definition def = typeEntry.getDefinition();
            Set<String> names = new TreeSet<String>();
            if ( isObjectType() ) {
                addImports(names, (ObjectTypeDefinition)def);
            } else if ( isInterfaceType() ) {
                addImports(names, (InterfaceTypeDefinition)def);
            } else if ( isInputObjectType() ) {
                addImports(names, (InputObjectTypeDefinition)def);
            } else if ( isUnionType() ) {
                addImports(names, (UnionTypeDefinition)def);
            } else if ( isEnumType() ) {
                addImports(names, (EnumTypeDefinition)def);
            } else if ( isSchemaType() ) {
                addImports(names, (SchemaDefinition)def);
            }
            imports = new ArrayList<>(names);
        }
        return imports;
    }

    public synchronized List<Field> getFields() {
        if ( null == fields ) {
            Definition def = typeEntry.getDefinition();
            if ( isObjectType() ) {
                fields = getFields((ObjectTypeDefinition)def);
            } else if ( isInterfaceType() ) {
                fields = getFields((InterfaceTypeDefinition)def);
            } else if ( isInputObjectType() ) {
                fields = getFields((InputObjectTypeDefinition)def);
            } else if ( isUnionType() ) {
                fields = getFields((UnionTypeDefinition)def);
            } else if ( isEnumType() ) {
                fields = getFields((EnumTypeDefinition)def);
            } else if ( isSchemaType() ) {
                fields = getFields((SchemaDefinition)def);
            } else {
                fields = Collections.emptyList();
            }
        }
        return fields;
    }

    private List<Field> getFields(ObjectTypeDefinition def) {
        List<Field> fields = new ArrayList<Field>();
        for ( FieldDefinition fieldDef : def.getFieldDefinitions() ) {
            // TODO: args...
            Field field = new Field(fieldDef.getName(), toJavaTypeName(fieldDef.getType()));
            field.graphQLType = toGraphQLType(fieldDef.getType());
            field.dataFetcher = toDataFetcher(fieldDef.getName(), fieldDef.getType());
            fields.add(field);
        }
        return fields;
    }

    private List<Field> getFields(InterfaceTypeDefinition def) {
        List<Field> fields = new ArrayList<Field>();
        for ( FieldDefinition fieldDef : def.getFieldDefinitions() ) {
            // TODO: args...
            fields.add(new Field(fieldDef.getName(), toJavaTypeName(fieldDef.getType())));
        }
        return fields;
    }

    private List<Field> getFields(InputObjectTypeDefinition def) {
        List<Field> fields = new ArrayList<Field>();
        for ( InputValueDefinition fieldDef : def.getInputValueDefinitions() ) {
            // TODO: Defult value...
            fields.add(new Field(fieldDef.getName(), toJavaTypeName(fieldDef.getType())));
        }
        return fields;
    }

    private List<Field> getFields(UnionTypeDefinition def) {
        List<Field> fields = new ArrayList<Field>();
        for ( Type type : def.getMemberTypes() ) {
            fields.add(new Field(null, toJavaTypeName(type)));
        }
        return fields;
    }

    private List<Field> getFields(EnumTypeDefinition def) {
        List<Field> fields = new ArrayList<Field>();
        for ( EnumValueDefinition fieldDef : def.getEnumValueDefinitions() ) {
            fields.add(new Field(fieldDef.getName(), null));
        }
        return fields;
    }

    private List<Field> getFields(SchemaDefinition def) {
        List<Field> fields = new ArrayList<Field>();
        for ( OperationTypeDefinition fieldDef : def.getOperationTypeDefinitions() ) {
            fields.add(new Field(fieldDef.getName(), toJavaTypeName(fieldDef.getType())));
        }
        return fields;
    }

    private DataFetcher toDataFetcher(String name, Type type) {
        if ( type instanceof ListType ) {
            DataFetcher fetcher = toDataFetcher(name, ((ListType)type).getType());
            if ( null == fetcher ) return null;
            fetcher.create = "new DataFetcherList(" + fetcher.create + ")";
            return fetcher;
        } else if ( type instanceof NonNullType ) {
            return toDataFetcher(name, ((NonNullType)type).getType());
        } else if ( type instanceof TypeName ) {
            String typeName = ((TypeName)type).getName();
            if ( BUILTINS.containsKey(typeName) ) return null;
            TypeEntry typeEntry = referenceTypes.get(typeName);
            if ( !typeEntry.hasIdField() ) return null;
            DataFetcher fetcher = new DataFetcher();
            fetcher.fieldType = typeName + "DataFetcher.Factory";
            fetcher.fieldName = "_" + lcFirst(typeName) + "Factory";
            fetcher.create =
                fetcher.fieldName + ".create(\""+name+"\")";
            return fetcher;
        } else {
            throw new UnsupportedOperationException("Unknown Type="+type.getClass().getName());
        }
    }

    private String toGraphQLType(Type type) {
        if ( type instanceof ListType ) {
            return "new GraphQLList(" + toGraphQLType(((ListType)type).getType()) + ")";
        } else if ( type instanceof NonNullType ) {
            return toGraphQLType(((NonNullType)type).getType());
        } else if ( type instanceof TypeName ) {
            String name = ((TypeName)type).getName();
            if ( BUILTINS.containsKey(name) ) {
                return "Scalars.GraphQL" + name;
            }
            return "new GraphQLTypeReference(\""+name+"\")";
        } else {
            throw new UnsupportedOperationException("Unknown Type="+type.getClass().getName());
        }
    }

    private String toJavaTypeName(Type type) {
        if ( type instanceof ListType ) {
            return "List<" + toJavaTypeName(((ListType)type).getType()) + ">";
        } else if ( type instanceof NonNullType ) {
            return toJavaTypeName(((NonNullType)type).getType());
        } else if ( type instanceof TypeName ) {
            String name = ((TypeName)type).getName();
            String rename = RENAME.get(name);
            // TODO: scalar type directive to get implementation class...
            if ( null != rename ) return rename;
            return name;
        } else {
            throw new UnsupportedOperationException("Unknown Type="+type.getClass().getName());
        }
    }

    private void addImports(Collection<String> imports, ObjectTypeDefinition def) {
        for ( FieldDefinition fieldDef : def.getFieldDefinitions() ) {
            addImports(imports, fieldDef.getType());
        }
    }

    private void addImports(Collection<String> imports, InterfaceTypeDefinition def) {
        for ( FieldDefinition fieldDef : def.getFieldDefinitions() ) {
            addImports(imports, fieldDef.getType());
        }
    }

    private void addImports(Collection<String> imports, InputObjectTypeDefinition def) {
        for ( InputValueDefinition fieldDef : def.getInputValueDefinitions() ) {
            addImports(imports, fieldDef.getType());
        }
    }

    private void addImports(Collection<String> imports, UnionTypeDefinition def) {
        for ( Type type : def.getMemberTypes() ) {
            addImports(imports, type);
        }
    }

    private void addImports(Collection<String> imports, EnumTypeDefinition def) {
        // No imports should be necessary...
    }

    private void addImports(Collection<String> imports, SchemaDefinition def) {
        for ( OperationTypeDefinition fieldDef : def.getOperationTypeDefinitions() ) {
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
                // TODO: scalar name may be different... should read annotations for scalars.
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
