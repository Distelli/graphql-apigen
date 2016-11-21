package com.distelli.graphql.apigen;

import java.util.Collections;
import graphql.language.ObjectTypeDefinition;
import graphql.language.TypeDefinition;
import graphql.language.Definition;
import graphql.language.Argument;
import graphql.language.Directive;
import graphql.language.InterfaceTypeDefinition;
import graphql.language.EnumTypeDefinition;
import graphql.language.ScalarTypeDefinition;
import graphql.language.UnionTypeDefinition;
import graphql.language.InputObjectTypeDefinition;
import graphql.language.SchemaDefinition;
import graphql.Scalars;
import java.util.List;
import java.net.URL;

public class TypeEntry {
    private URL source;
    private Definition definition;
    private String packageName;

    public TypeEntry(Definition definition, URL source) {
        this.source = source;
        this.definition = definition;
        this.packageName = getPackageName(getDirectives(definition));
    }

    public URL getSource() {
        return source;
    }

    // Return nice formatted string for source location:
    public String getSourceLocation() {
        return source + ":[" + definition.getSourceLocation().getLine() +
            ", " + definition.getSourceLocation().getColumn() + "]";
    }

    public String getPackageName() {
        return packageName;
    }

    public String getName() {
        if ( definition instanceof TypeDefinition ) {
            return ((TypeDefinition)definition).getName();
        }
        return "";
    }

    public Definition getDefinition() {
        return definition;
    }

    public boolean hasIdField() {
        if ( definition instanceof ObjectTypeDefinition) {
            return ((ObjectTypeDefinition)definition).getFieldDefinitions()
                .stream()
                .anyMatch((field) -> "id".equals(field.getName()));
        }
        return false;
    }

    private static List<Directive> getDirectives(Definition def) {
        if ( def instanceof ObjectTypeDefinition ) {
            return ((ObjectTypeDefinition)def).getDirectives();
        } if ( def instanceof InterfaceTypeDefinition ) {
            return ((InterfaceTypeDefinition)def).getDirectives();
        } if ( def instanceof EnumTypeDefinition ) {
            return ((EnumTypeDefinition)def).getDirectives();
        } if ( def instanceof ScalarTypeDefinition ) {
            return ((ScalarTypeDefinition)def).getDirectives();
        } if ( def instanceof UnionTypeDefinition ) {
            return ((UnionTypeDefinition)def).getDirectives();
        } if ( def instanceof InputObjectTypeDefinition ) {
            return ((InputObjectTypeDefinition)def).getDirectives();
        } if ( def instanceof SchemaDefinition ) {
            return ((SchemaDefinition)def).getDirectives();
        }
        return Collections.emptyList();
    }

    private static String getPackageName(List<Directive> directives) {
        String packageName = null;
        for ( Directive directive : directives ) {
            if ( ! "java".equals(directive.getName()) ) continue;
            for ( Argument arg : directive.getArguments() ) {
                if ( ! "package".equals(arg.getName()) ) continue;
                packageName = (String)Scalars.GraphQLString.getCoercing().parseLiteral(arg.getValue());
                break;
            }
            break;
        }
        return ( null == packageName ) ? "com.graphql.generated" : packageName;
    }

}
